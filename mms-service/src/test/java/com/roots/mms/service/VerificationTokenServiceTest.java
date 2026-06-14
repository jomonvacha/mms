package com.roots.mms.service;

import com.roots.mms.entity.AuthProvider;
import com.roots.mms.entity.User;
import com.roots.mms.entity.VerificationToken;
import com.roots.mms.exception.BusinessRuleException;
import com.roots.mms.exception.ResourceNotFoundException;
import com.roots.mms.repository.UserRepository;
import com.roots.mms.repository.VerificationTokenRepository;
import com.roots.mms.service.email.EmailService;
import com.roots.mms.service.email.OutgoingEmail;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class VerificationTokenServiceTest {

    private VerificationTokenRepository tokenRepo;
    private UserRepository userRepo;
    private PasswordEncoder passwordEncoder;
    private EmailService emailService;
    private VerificationTokenService service;

    @BeforeEach
    void setUp() {
        tokenRepo = mock(VerificationTokenRepository.class);
        userRepo = mock(UserRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);
        emailService = mock(EmailService.class);
        service = new VerificationTokenService(tokenRepo, userRepo, passwordEncoder, emailService);

        // Wire in @Value defaults (Spring isn't running in this unit test).
        ReflectionTestUtils.setField(service, "resetTtlMinutes", 30L);
        ReflectionTestUtils.setField(service, "verifyTtlHours", 48L);
        ReflectionTestUtils.setField(service, "resetUrlBase", "https://app.example.com/reset?token=");
        ReflectionTestUtils.setField(service, "verifyUrlBase", "https://app.example.com/verify?token=");
    }

    private User localUser(String email) {
        User u = new User("alice", email, "encoded-pw", "Alice", "Smith");
        u.setId(UUID.randomUUID());
        u.setProvider(AuthProvider.LOCAL);
        return u;
    }

    // ── startPasswordReset ────────────────────────────────────────────

    @Test
    void startPasswordReset_unknownEmail_silentlyReturns() {
        when(userRepo.findByEmail("nobody@example.com")).thenReturn(Optional.empty());

        service.startPasswordReset("nobody@example.com");

        // Non-enumerating: never reveal that the email is unknown — no token
        // saved, no email sent, no exception.
        verify(tokenRepo, never()).save(any());
        verify(emailService, never()).send(any());
    }

    @Test
    void startPasswordReset_googleProvider_sendsExplanationEmail() {
        User u = localUser("oauth@example.com");
        u.setProvider(AuthProvider.GOOGLE);
        when(userRepo.findByEmail("oauth@example.com")).thenReturn(Optional.of(u));

        service.startPasswordReset("oauth@example.com");

        var captor = forClass(OutgoingEmail.class);
        verify(emailService).send(captor.capture());
        OutgoingEmail email = captor.getValue();
        assertThat(email.to()).isEqualTo("oauth@example.com");
        assertThat(email.body()).contains("google");
        // Critical: no reset token issued for federated accounts.
        verify(tokenRepo, never()).save(any());
    }

    @Test
    void startPasswordReset_local_clearsExistingTokens_savesNew_sendsEmail() {
        User u = localUser("alice@example.com");
        when(userRepo.findByEmail("alice@example.com")).thenReturn(Optional.of(u));

        service.startPasswordReset("alice@example.com");

        verify(tokenRepo).deleteByUserIdAndType(u.getId(), VerificationToken.TokenType.PASSWORD_RESET);

        var tokenCaptor = forClass(VerificationToken.class);
        verify(tokenRepo).save(tokenCaptor.capture());
        VerificationToken saved = tokenCaptor.getValue();
        assertThat(saved.getType()).isEqualTo(VerificationToken.TokenType.PASSWORD_RESET);
        assertThat(saved.getUserId()).isEqualTo(u.getId());
        assertThat(saved.getToken()).isNotBlank();
        assertThat(saved.getExpiresAt()).isAfter(Instant.now());

        var emailCaptor = forClass(OutgoingEmail.class);
        verify(emailService).send(emailCaptor.capture());
        OutgoingEmail email = emailCaptor.getValue();
        assertThat(email.body()).contains("Alice").contains(saved.getToken()).contains("30 minutes");
        assertThat(email.subject()).isEqualToIgnoringCase("Reset your password");
    }

    @Test
    void startPasswordReset_userWithBlankFirstName_emailUsesFallbackGreeting() {
        User u = localUser("alice@example.com");
        u.setFirstName("");
        when(userRepo.findByEmail("alice@example.com")).thenReturn(Optional.of(u));

        service.startPasswordReset("alice@example.com");

        var emailCaptor = forClass(OutgoingEmail.class);
        verify(emailService).send(emailCaptor.capture());
        assertThat(emailCaptor.getValue().body()).contains("Hi there");
    }

    // ── startEmailVerification ────────────────────────────────────────

    @Test
    void startEmailVerification_nullUser_returnsSilently() {
        service.startEmailVerification(null);

        verify(tokenRepo, never()).save(any());
        verify(emailService, never()).send(any());
    }

    @Test
    void startEmailVerification_userWithoutEmail_returnsSilently() {
        User u = localUser(null);
        u.setEmail(null);

        service.startEmailVerification(u);

        verify(tokenRepo, never()).save(any());
        verify(emailService, never()).send(any());
    }

    @Test
    void startEmailVerification_savesTokenAndSendsEmail() {
        User u = localUser("new@example.com");

        service.startEmailVerification(u);

        verify(tokenRepo).deleteByUserIdAndType(u.getId(), VerificationToken.TokenType.EMAIL_VERIFICATION);

        var tokenCaptor = forClass(VerificationToken.class);
        verify(tokenRepo).save(tokenCaptor.capture());
        VerificationToken saved = tokenCaptor.getValue();
        assertThat(saved.getType()).isEqualTo(VerificationToken.TokenType.EMAIL_VERIFICATION);

        var emailCaptor = forClass(OutgoingEmail.class);
        verify(emailService).send(emailCaptor.capture());
        assertThat(emailCaptor.getValue().body()).contains("48 hours").contains(saved.getToken());
    }

    // ── redeemPasswordReset ───────────────────────────────────────────

    @Test
    void redeemPasswordReset_blankToken_throws() {
        assertThatThrownBy(() -> service.redeemPasswordReset("  ", "Abcdefg1"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("required");
    }

    @Test
    void redeemPasswordReset_unknownToken_throws() {
        when(tokenRepo.findByToken("xyz")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.redeemPasswordReset("xyz", "Abcdefg1"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Invalid or expired");
    }

    @Test
    void redeemPasswordReset_wrongTokenType_throws() {
        // A token of type EMAIL_VERIFICATION must not satisfy a PASSWORD_RESET
        // redemption — covers a class of bug where the token table is queried
        // by raw value without type-checking.
        VerificationToken t = VerificationToken.builder()
                .token("xyz").userId(UUID.randomUUID())
                .type(VerificationToken.TokenType.EMAIL_VERIFICATION)
                .createdAt(Instant.now()).expiresAt(Instant.now().plusSeconds(600)).build();
        when(tokenRepo.findByToken("xyz")).thenReturn(Optional.of(t));

        assertThatThrownBy(() -> service.redeemPasswordReset("xyz", "Abcdefg1"))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void redeemPasswordReset_alreadyConsumed_throws() {
        VerificationToken t = VerificationToken.builder()
                .token("xyz").userId(UUID.randomUUID())
                .type(VerificationToken.TokenType.PASSWORD_RESET)
                .createdAt(Instant.now()).expiresAt(Instant.now().plusSeconds(600))
                .consumedAt(Instant.now()).build();
        when(tokenRepo.findByToken("xyz")).thenReturn(Optional.of(t));

        assertThatThrownBy(() -> service.redeemPasswordReset("xyz", "Abcdefg1"))
                .hasMessageContaining("no longer valid");
    }

    @Test
    void redeemPasswordReset_expired_throws() {
        VerificationToken t = VerificationToken.builder()
                .token("xyz").userId(UUID.randomUUID())
                .type(VerificationToken.TokenType.PASSWORD_RESET)
                .createdAt(Instant.now().minusSeconds(7200))
                .expiresAt(Instant.now().minusSeconds(60)).build();
        when(tokenRepo.findByToken("xyz")).thenReturn(Optional.of(t));

        assertThatThrownBy(() -> service.redeemPasswordReset("xyz", "Abcdefg1"))
                .hasMessageContaining("expired");
    }

    @Test
    void redeemPasswordReset_weakPassword_throws() {
        VerificationToken t = VerificationToken.builder()
                .token("xyz").userId(UUID.randomUUID())
                .type(VerificationToken.TokenType.PASSWORD_RESET)
                .expiresAt(Instant.now().plusSeconds(600)).build();
        when(tokenRepo.findByToken("xyz")).thenReturn(Optional.of(t));

        // Too short
        assertThatThrownBy(() -> service.redeemPasswordReset("xyz", "Ab1"))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("8");
        // No digit
        assertThatThrownBy(() -> service.redeemPasswordReset("xyz", "abcdefgh"))
                .isInstanceOf(BusinessRuleException.class);
        // No letter
        assertThatThrownBy(() -> service.redeemPasswordReset("xyz", "12345678"))
                .isInstanceOf(BusinessRuleException.class);
        // Too long (65 chars)
        assertThatThrownBy(() -> service.redeemPasswordReset("xyz", "A1" + "x".repeat(63)))
                .isInstanceOf(BusinessRuleException.class);

        // None of the rejected attempts should have touched the user record.
        verify(userRepo, never()).save(any());
    }

    @Test
    void redeemPasswordReset_userMissing_throwsResourceNotFound() {
        UUID uid = UUID.randomUUID();
        VerificationToken t = VerificationToken.builder()
                .token("xyz").userId(uid)
                .type(VerificationToken.TokenType.PASSWORD_RESET)
                .expiresAt(Instant.now().plusSeconds(600)).build();
        when(tokenRepo.findByToken("xyz")).thenReturn(Optional.of(t));
        when(userRepo.findById(uid)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.redeemPasswordReset("xyz", "Abcdefg1"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void redeemPasswordReset_success_encodesPassword_marksConsumed() {
        UUID uid = UUID.randomUUID();
        User user = localUser("alice@example.com");
        user.setId(uid);
        VerificationToken t = VerificationToken.builder()
                .token("xyz").userId(uid)
                .type(VerificationToken.TokenType.PASSWORD_RESET)
                .expiresAt(Instant.now().plusSeconds(600)).build();
        when(tokenRepo.findByToken("xyz")).thenReturn(Optional.of(t));
        when(userRepo.findById(uid)).thenReturn(Optional.of(user));
        when(passwordEncoder.encode("Abcdefg1")).thenReturn("hashed-pw");

        service.redeemPasswordReset("xyz", "Abcdefg1");

        assertThat(user.getPassword()).isEqualTo("hashed-pw");
        verify(userRepo).save(user);
        assertThat(t.getConsumedAt()).isNotNull();
        verify(tokenRepo).save(t);
    }

    // ── redeemEmailVerification ───────────────────────────────────────

    @Test
    void redeemEmailVerification_setsEmailVerifiedAndConsumes() {
        UUID uid = UUID.randomUUID();
        User user = localUser("alice@example.com");
        user.setId(uid);
        user.setEmailVerified(false);
        VerificationToken t = VerificationToken.builder()
                .token("xyz").userId(uid)
                .type(VerificationToken.TokenType.EMAIL_VERIFICATION)
                .expiresAt(Instant.now().plusSeconds(600)).build();
        when(tokenRepo.findByToken("xyz")).thenReturn(Optional.of(t));
        when(userRepo.findById(uid)).thenReturn(Optional.of(user));

        service.redeemEmailVerification("xyz");

        assertThat(user.getEmailVerified()).isTrue();
        verify(userRepo).save(user);
        assertThat(t.getConsumedAt()).isNotNull();
        verify(tokenRepo).save(t);
    }

    @Test
    void redeemEmailVerification_passwordResetTokenRejected() {
        VerificationToken t = VerificationToken.builder()
                .token("xyz").userId(UUID.randomUUID())
                .type(VerificationToken.TokenType.PASSWORD_RESET)
                .expiresAt(Instant.now().plusSeconds(600)).build();
        when(tokenRepo.findByToken("xyz")).thenReturn(Optional.of(t));

        assertThatThrownBy(() -> service.redeemEmailVerification("xyz"))
                .isInstanceOf(BusinessRuleException.class);
        verify(userRepo, never()).save(any());
    }

    @Test
    void redeemEmailVerification_userMissing_throws() {
        UUID uid = UUID.randomUUID();
        VerificationToken t = VerificationToken.builder()
                .token("xyz").userId(uid)
                .type(VerificationToken.TokenType.EMAIL_VERIFICATION)
                .expiresAt(Instant.now().plusSeconds(600)).build();
        when(tokenRepo.findByToken("xyz")).thenReturn(Optional.of(t));
        when(userRepo.findById(uid)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.redeemEmailVerification("xyz"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ── token uniqueness sanity check ─────────────────────────────────

    @Test
    void generateToken_yieldsDistinctValuesAcrossCalls() {
        // Two consecutive issuances should not collide. We capture the saved
        // tokens from two startEmailVerification calls and assert they differ.
        User u1 = localUser("a@example.com");
        User u2 = localUser("b@example.com");

        service.startEmailVerification(u1);
        service.startEmailVerification(u2);

        var captor = forClass(VerificationToken.class);
        verify(tokenRepo, times(2)).save(captor.capture());
        var tokens = captor.getAllValues();
        assertThat(tokens.get(0).getToken()).isNotEqualTo(tokens.get(1).getToken());
        // Sanity: URL-safe base64 (no '+' or '/'), no padding.
        assertThat(tokens.get(0).getToken()).matches("[A-Za-z0-9_-]+");
    }
}
