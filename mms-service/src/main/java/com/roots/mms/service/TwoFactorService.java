package com.roots.mms.service;

import com.roots.mms.entity.User;
import com.roots.mms.exception.BusinessRuleException;
import com.roots.mms.exception.ResourceNotFoundException;
import com.roots.mms.repository.UserRepository;
import dev.samstevens.totp.code.CodeGenerator;
import dev.samstevens.totp.code.CodeVerifier;
import dev.samstevens.totp.code.DefaultCodeGenerator;
import dev.samstevens.totp.code.DefaultCodeVerifier;
import dev.samstevens.totp.code.HashingAlgorithm;
import dev.samstevens.totp.secret.DefaultSecretGenerator;
import dev.samstevens.totp.secret.SecretGenerator;
import dev.samstevens.totp.time.SystemTimeProvider;
import dev.samstevens.totp.time.TimeProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * TOTP-based 2FA. Users scan a QR code generated from an {@code otpauth://} URI
 * in their authenticator app (Google Authenticator, 1Password, Authy, etc.) and
 * then enter a 6-digit code to confirm setup. Recovery codes are issued at setup
 * time in case the authenticator is lost.
 *
 * <p>Secrets are stored plaintext in the User document so the server can verify
 * codes on sign-in. Recovery codes are stored <em>hashed</em> (BCrypt) so a DB
 * compromise doesn't reveal them. Each code is single-use.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TwoFactorService {

    private static final SecureRandom RNG = new SecureRandom();
    private static final int RECOVERY_CODE_COUNT = 10;
    private static final String RECOVERY_ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.auth.totp-issuer:Roots MMS}")
    private String issuer;

    private final SecretGenerator secretGenerator = new DefaultSecretGenerator();
    private final TimeProvider timeProvider = new SystemTimeProvider();
    private final CodeGenerator codeGenerator = new DefaultCodeGenerator(HashingAlgorithm.SHA1, 6);
    private final CodeVerifier codeVerifier = new DefaultCodeVerifier(codeGenerator, timeProvider);

    public record SetupResult(String secret, String otpauthUri, String qrDataUri) {}

    public record EnableResult(List<String> recoveryCodes) {}

    // ── Setup flow ──────────────────────────────────────────────────────────

    /**
     * Starts 2FA setup: generates a fresh secret and stores it on the user
     * (but with {@code totpEnabled=false}). The caller then verifies with a
     * code from their authenticator via {@link #confirmSetup(String, String)}.
     */
    public SetupResult startSetup(String userId) {
        User user = userRepository.findById(java.util.UUID.fromString(userId))
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));

        if (Boolean.TRUE.equals(user.getTotpEnabled())) {
            throw new BusinessRuleException("2FA is already enabled. Disable it first to re-enroll.");
        }
        String secret = secretGenerator.generate();
        user.setTotpSecret(secret);
        user.setTotpEnabled(false);
        userRepository.save(user);

        String otpauth = buildOtpauthUri(secret, user.getEmail());
        // Return a data URI the UI can feed into <img src>; we avoid a QR-image
        // dependency on the server by letting the UI render the URI client-side,
        // but we also return the otpauth URI directly for copy-paste setup.
        return new SetupResult(secret, otpauth, "");
    }

    /**
     * Confirms 2FA setup. The user enters a code from their authenticator to
     * prove the secret was enrolled correctly. On success, 2FA is enabled and
     * 10 one-time recovery codes are returned (shown exactly once).
     */
    public EnableResult confirmSetup(String userId, String code) {
        User user = userRepository.findById(java.util.UUID.fromString(userId))
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));
        if (user.getTotpSecret() == null) {
            throw new BusinessRuleException("2FA setup has not been started.");
        }
        if (!codeVerifier.isValidCode(user.getTotpSecret(), code)) {
            throw new BusinessRuleException("Invalid authentication code.");
        }

        // Generate plaintext recovery codes to return to the user, store hashed copies
        List<String> plaintext = new ArrayList<>(RECOVERY_CODE_COUNT);
        List<String> hashed = new ArrayList<>(RECOVERY_CODE_COUNT);
        for (int i = 0; i < RECOVERY_CODE_COUNT; i++) {
            String c = randomRecoveryCode();
            plaintext.add(c);
            hashed.add(passwordEncoder.encode(c));
        }
        user.setTotpEnabled(true);
        user.setTotpRecoveryCodes(hashed);
        userRepository.save(user);
        log.info("2FA enabled for userId={}", userId);
        return new EnableResult(plaintext);
    }

    /**
     * Disables 2FA on the user's account after re-verifying their identity
     * with either their current password or a TOTP code.
     */
    public void disable(String userId, String currentPasswordOrCode) {
        User user = userRepository.findById(java.util.UUID.fromString(userId))
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));
        if (!Boolean.TRUE.equals(user.getTotpEnabled())) {
            throw new BusinessRuleException("2FA is not currently enabled.");
        }
        boolean verified = false;
        if (user.getPassword() != null && !user.getPassword().isBlank()
                && passwordEncoder.matches(currentPasswordOrCode, user.getPassword())) {
            verified = true;
        } else if (user.getTotpSecret() != null
                && codeVerifier.isValidCode(user.getTotpSecret(), currentPasswordOrCode)) {
            verified = true;
        }
        if (!verified) {
            throw new BusinessRuleException("Invalid password or authentication code.");
        }
        user.setTotpEnabled(false);
        user.setTotpSecret(null);
        user.setTotpRecoveryCodes(null);
        userRepository.save(user);
        log.info("2FA disabled for userId={}", userId);
    }

    /**
     * Mints a fresh set of one-time recovery codes, replacing any unused ones.
     * Available any time 2FA is enabled (TradingView "Generate new codes"
     * parity), not just at first setup. Returns the new plaintext codes, which
     * are shown to the user exactly once.
     */
    public EnableResult regenerateRecoveryCodes(String userId) {
        User user = userRepository.findById(java.util.UUID.fromString(userId))
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));
        if (!Boolean.TRUE.equals(user.getTotpEnabled())) {
            throw new BusinessRuleException("Enable two-factor authentication first.");
        }
        List<String> plaintext = new ArrayList<>(RECOVERY_CODE_COUNT);
        List<String> hashed = new ArrayList<>(RECOVERY_CODE_COUNT);
        for (int i = 0; i < RECOVERY_CODE_COUNT; i++) {
            String c = randomRecoveryCode();
            plaintext.add(c);
            hashed.add(passwordEncoder.encode(c));
        }
        user.setTotpRecoveryCodes(hashed);
        userRepository.save(user);
        log.info("Recovery codes regenerated for userId={}", userId);
        return new EnableResult(plaintext);
    }

    // ── Signin verification ─────────────────────────────────────────────────

    /**
     * Validates a TOTP code or a recovery code for sign-in. If the input
     * matches a stored recovery code, that code is consumed (removed from the
     * user's list). Returns true on success.
     */
    public boolean verifyDuringSignin(User user, String code) {
        if (user.getTotpSecret() != null && codeVerifier.isValidCode(user.getTotpSecret(), code)) {
            return true;
        }
        // Recovery code fallback
        List<String> recovery = user.getTotpRecoveryCodes();
        if (recovery != null && !recovery.isEmpty()) {
            for (int i = 0; i < recovery.size(); i++) {
                if (passwordEncoder.matches(code, recovery.get(i))) {
                    recovery.remove(i);
                    user.setTotpRecoveryCodes(recovery);
                    userRepository.save(user);
                    log.warn("Recovery code consumed for userId={} ({} remaining)", user.getId(), recovery.size());
                    return true;
                }
            }
        }
        return false;
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    private String buildOtpauthUri(String secret, String label) {
        // otpauth://totp/{issuer}:{label}?secret={secret}&issuer={issuer}&algorithm=SHA1&digits=6&period=30
        String enc = StandardCharsets.UTF_8.name();
        try {
            String encIssuer = URLEncoder.encode(issuer, enc);
            String encLabel = URLEncoder.encode(label != null ? label : "user", enc);
            return "otpauth://totp/" + encIssuer + ":" + encLabel
                    + "?secret=" + secret
                    + "&issuer=" + encIssuer
                    + "&algorithm=SHA1&digits=6&period=30";
        } catch (Exception e) {
            throw new IllegalStateException("Two-factor setup could not be completed. Please try again.", e);
        }
    }

    private static String randomRecoveryCode() {
        // 10 chars, grouped as 5-5 with a dash, e.g. A3K9M-QRSTV
        StringBuilder sb = new StringBuilder(11);
        for (int i = 0; i < 10; i++) {
            if (i == 5) sb.append('-');
            sb.append(RECOVERY_ALPHABET.charAt(RNG.nextInt(RECOVERY_ALPHABET.length())));
        }
        return sb.toString();
    }

    // Unused, reserved for a future "generate QR PNG server-side" feature.
    @SuppressWarnings("unused")
    private static String base64(byte[] b) {
        return Base64.getEncoder().encodeToString(b);
    }
}
