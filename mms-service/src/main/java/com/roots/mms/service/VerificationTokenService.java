package com.roots.mms.service;

import com.roots.mms.entity.User;
import com.roots.mms.entity.VerificationToken;
import com.roots.mms.exception.BusinessRuleException;
import com.roots.mms.exception.ResourceNotFoundException;
import com.roots.mms.repository.UserRepository;
import com.roots.mms.repository.VerificationTokenRepository;
import com.roots.mms.service.email.EmailService;
import com.roots.mms.service.email.OutgoingEmail;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;

/**
 * Issues, stores, and redeems short-lived one-time tokens for password resets
 * and email verification. Tokens are 256 bits, generated with {@link SecureRandom}
 * and transmitted in email links; they're stored in MongoDB with a TTL index so
 * expired tokens are reaped automatically.
 *
 * <p>Forgot-password is intentionally non-enumerating: the caller always gets
 * a 200 response whether or not the email exists in the database, and we only
 * log at DEBUG level on misses.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class VerificationTokenService {

    private static final SecureRandom RNG = new SecureRandom();
    private static final Base64.Encoder URL_B64 = Base64.getUrlEncoder().withoutPadding();

    private final VerificationTokenRepository tokenRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;

    @Value("${app.auth.password-reset-ttl-minutes:30}")
    private long resetTtlMinutes;

    @Value("${app.auth.email-verification-ttl-hours:48}")
    private long verifyTtlHours;

    @Value("${app.auth.password-reset-url:http://localhost:3001/reset-password?token=}")
    private String resetUrlBase;

    @Value("${app.auth.email-verification-url:http://localhost:3001/verify-email?token=}")
    private String verifyUrlBase;

    @Value("${app.auth.email-change-url:http://localhost:3001/confirm-email-change?token=}")
    private String emailChangeUrlBase;

    // ── Issue ───────────────────────────────────────────────────────────────

    /**
     * Starts a password-reset flow. Does nothing (silently) if the email is
     * not registered, to avoid exposing which accounts exist. Always safe to
     * call from an unauthenticated endpoint.
     */
    public void startPasswordReset(String email) {
        Optional<User> maybe = userRepository.findByEmail(email);
        if (maybe.isEmpty()) {
            log.debug("Password reset requested for unknown email: {}", email);
            return;
        }
        User user = maybe.get();
        if (user.getProvider() != null && user.getProvider() != com.roots.mms.entity.AuthProvider.LOCAL) {
            // Federated accounts don't have a local password to reset; send a
            // friendly explanatory email instead of pretending to reset.
            emailService.send(OutgoingEmail.of(
                    email,
                    "Reset your password",
                    "Your " + user.getProvider().name().toLowerCase()
                            + " account was signed up via single sign-on, so there's no local password "
                            + "to reset. Please sign in using " + user.getProvider().name().toLowerCase()
                            + " instead."));
            return;
        }
        // Invalidate any existing reset token for this user
        tokenRepository.deleteByUserIdAndType(user.getId(), VerificationToken.TokenType.PASSWORD_RESET);

        String raw = generateToken();
        VerificationToken t = VerificationToken.builder()
                .token(raw)
                .userId(user.getId())
                .type(VerificationToken.TokenType.PASSWORD_RESET)
                .createdAt(Instant.now())
                .expiresAt(Instant.now().plus(Duration.ofMinutes(resetTtlMinutes)))
                .build();
        tokenRepository.save(t);

        String link = resetUrlBase + raw;
        String body = String.format("""
                Hi %s,

                We received a request to reset your password. Click the link below to
                choose a new password (valid for %d minutes):

                %s

                If you didn't request this, you can safely ignore this email.
                """, firstNameOrFallback(user), resetTtlMinutes, link);

        emailService.send(OutgoingEmail.of(email, "Reset your password", body));
    }

    /**
     * Issues a fresh email-verification token for a just-signed-up user and
     * sends the verification email. Called from the signup flow.
     */
    public void startEmailVerification(User user) {
        if (user == null || user.getEmail() == null) return;
        tokenRepository.deleteByUserIdAndType(user.getId(), VerificationToken.TokenType.EMAIL_VERIFICATION);

        String raw = generateToken();
        VerificationToken t = VerificationToken.builder()
                .token(raw)
                .userId(user.getId())
                .type(VerificationToken.TokenType.EMAIL_VERIFICATION)
                .createdAt(Instant.now())
                .expiresAt(Instant.now().plus(Duration.ofHours(verifyTtlHours)))
                .build();
        tokenRepository.save(t);

        String link = verifyUrlBase + raw;
        String body = String.format("""
                Hi %s,

                Welcome to Roots MMS! Please confirm your email address by clicking
                the link below (valid for %d hours):

                %s
                """, firstNameOrFallback(user), verifyTtlHours, link);

        emailService.send(OutgoingEmail.of(user.getEmail(), "Verify your email", body));
    }

    /**
     * Starts a verified email-change. Emails a confirmation link to the NEW
     * address and a heads-up notice to the OLD address (so a compromised account
     * can't silently move its email). The change only takes effect once the new
     * address is confirmed via {@link #redeemEmailChange(String)}.
     */
    public void startEmailChange(User user, String newEmail) {
        if (user == null || newEmail == null || newEmail.isBlank()) return;
        tokenRepository.deleteByUserIdAndType(user.getId(), VerificationToken.TokenType.EMAIL_CHANGE);

        String raw = generateToken();
        VerificationToken t = VerificationToken.builder()
                .token(raw)
                .userId(user.getId())
                .type(VerificationToken.TokenType.EMAIL_CHANGE)
                .newEmail(newEmail)
                .createdAt(Instant.now())
                .expiresAt(Instant.now().plus(Duration.ofHours(verifyTtlHours)))
                .build();
        tokenRepository.save(t);

        String link = emailChangeUrlBase + raw;
        String confirmBody = String.format("""
                Hi %s,

                Please confirm that you want to use this address for your account by
                clicking the link below (valid for %d hours):

                %s

                If you didn't request this change, you can ignore this email.
                """, firstNameOrFallback(user), verifyTtlHours, link);
        emailService.send(OutgoingEmail.of(newEmail, "Confirm your new email address", confirmBody));

        // Heads-up to the current address — never contains the confirmation link.
        String noticeBody = String.format("""
                Hi %s,

                We received a request to change the email address on your account to
                %s. If this wasn't you, please change your password immediately and
                review your active sessions — your current email has not been changed.
                """, firstNameOrFallback(user), newEmail);
        emailService.send(OutgoingEmail.of(user.getEmail(), "Email change requested", noticeBody));
    }

    // ── Redeem ──────────────────────────────────────────────────────────────

    /**
     * Consumes a password-reset token and sets a new password on the account.
     * Throws BusinessRuleException if the token is unknown, expired, already
     * used, or the new password doesn't meet complexity rules.
     */
    public void redeemPasswordReset(String token, String newPassword) {
        VerificationToken t = loadValid(token, VerificationToken.TokenType.PASSWORD_RESET);
        validatePasswordComplexity(newPassword);

        User user = userRepository.findById(t.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", t.getUserId()));

        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        t.setConsumedAt(Instant.now());
        tokenRepository.save(t);
        log.info("Password reset completed for userId={}", user.getId());
    }

    /**
     * Consumes an email-verification token and marks the user's email as verified.
     */
    public void redeemEmailVerification(String token) {
        VerificationToken t = loadValid(token, VerificationToken.TokenType.EMAIL_VERIFICATION);
        User user = userRepository.findById(t.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", t.getUserId()));

        user.setEmailVerified(true);
        userRepository.save(user);

        t.setConsumedAt(Instant.now());
        tokenRepository.save(t);
        log.info("Email verified for userId={}", user.getId());
    }

    /**
     * Consumes an email-change token and moves the account to the new address,
     * marking it verified. Re-checks uniqueness at redeem time in case the
     * address was taken between request and confirmation.
     */
    public void redeemEmailChange(String token) {
        VerificationToken t = loadValid(token, VerificationToken.TokenType.EMAIL_CHANGE);
        String newEmail = t.getNewEmail();
        if (newEmail == null || newEmail.isBlank()) {
            throw new BusinessRuleException("Invalid or expired token");
        }
        User user = userRepository.findById(t.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", t.getUserId()));

        if (!newEmail.equalsIgnoreCase(user.getEmail())
                && userRepository.existsByEmail(newEmail)) {
            throw new BusinessRuleException("That email address is no longer available.");
        }

        user.setEmail(newEmail);
        user.setEmailVerified(true);
        userRepository.save(user);

        t.setConsumedAt(Instant.now());
        tokenRepository.save(t);
        log.info("Email changed for userId={}", user.getId());
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    private VerificationToken loadValid(String raw, VerificationToken.TokenType expectedType) {
        if (raw == null || raw.isBlank()) throw new BusinessRuleException("Token is required");
        VerificationToken t = tokenRepository.findByToken(raw)
                .orElseThrow(() -> new BusinessRuleException("Invalid or expired token"));
        if (t.getType() != expectedType) {
            throw new BusinessRuleException("Invalid or expired token");
        }
        if (t.getConsumedAt() != null) {
            throw new BusinessRuleException("This link is no longer valid.");
        }
        if (t.getExpiresAt() != null && Instant.now().isAfter(t.getExpiresAt())) {
            throw new BusinessRuleException("Token has expired");
        }
        return t;
    }

    private static void validatePasswordComplexity(String pw) {
        if (pw == null || pw.length() < 8 || pw.length() > 64
                || !pw.matches(".*[A-Za-z].*") || !pw.matches(".*\\d.*")) {
            throw new BusinessRuleException(
                    "Password must be 8–64 characters and contain at least one letter and one number");
        }
    }

    private static String generateToken() {
        byte[] buf = new byte[32];
        RNG.nextBytes(buf);
        return URL_B64.encodeToString(buf);
    }

    private static String firstNameOrFallback(User user) {
        return (user.getFirstName() != null && !user.getFirstName().isBlank())
                ? user.getFirstName()
                : "there";
    }
}
