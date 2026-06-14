package com.roots.mms.service;

import com.roots.mms.entity.User;
import com.roots.mms.exception.BusinessRuleException;
import com.roots.mms.exception.ResourceNotFoundException;
import com.roots.mms.repository.UserRepository;
import dev.samstevens.totp.code.CodeVerifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TwoFactorServiceTest {

    private UserRepository userRepo;
    private PasswordEncoder passwordEncoder;
    private CodeVerifier codeVerifier;
    private TwoFactorService service;

    @BeforeEach
    void setUp() {
        userRepo = mock(UserRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);
        codeVerifier = mock(CodeVerifier.class);
        service = new TwoFactorService(userRepo, passwordEncoder);
        // The TOTP CodeVerifier is created in the field initializer (final).
        // Swap it for a mock so we can deterministically control "valid code".
        ReflectionTestUtils.setField(service, "codeVerifier", codeVerifier);
        ReflectionTestUtils.setField(service, "issuer", "Roots MMS");
    }

    private User user() {
        User u = new User("alice", "alice@example.com", "encoded-pw", "Alice", "Smith");
        u.setId(UUID.randomUUID());
        return u;
    }

    // ── startSetup ────────────────────────────────────────────────────

    @Test
    void startSetup_unknownUser_throws() {
        UUID id = UUID.randomUUID();
        when(userRepo.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.startSetup(id.toString()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void startSetup_alreadyEnabled_throws() {
        User u = user();
        u.setTotpEnabled(true);
        when(userRepo.findById(u.getId())).thenReturn(Optional.of(u));

        assertThatThrownBy(() -> service.startSetup(u.getId().toString()))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("already enabled");
        verify(userRepo, never()).save(any());
    }

    @Test
    void startSetup_freshUser_assignsSecretAndReturnsOtpauthUri() {
        User u = user();
        when(userRepo.findById(u.getId())).thenReturn(Optional.of(u));

        TwoFactorService.SetupResult result = service.startSetup(u.getId().toString());

        assertThat(result.secret()).isNotBlank();
        assertThat(u.getTotpSecret()).isEqualTo(result.secret());
        assertThat(u.getTotpEnabled()).isFalse();
        assertThat(result.otpauthUri())
                .startsWith("otpauth://totp/Roots+MMS:alice%40example.com")
                .contains("secret=" + result.secret())
                .contains("algorithm=SHA1")
                .contains("digits=6")
                .contains("period=30");
        verify(userRepo).save(u);
    }

    // ── confirmSetup ──────────────────────────────────────────────────

    @Test
    void confirmSetup_unknownUser_throws() {
        UUID id = UUID.randomUUID();
        when(userRepo.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.confirmSetup(id.toString(), "123456"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void confirmSetup_setupNotStarted_throws() {
        User u = user();
        u.setTotpSecret(null);
        when(userRepo.findById(u.getId())).thenReturn(Optional.of(u));

        assertThatThrownBy(() -> service.confirmSetup(u.getId().toString(), "123456"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("setup has not been started");
    }

    @Test
    void confirmSetup_invalidCode_throws() {
        User u = user();
        u.setTotpSecret("SECRET");
        when(userRepo.findById(u.getId())).thenReturn(Optional.of(u));
        when(codeVerifier.isValidCode("SECRET", "000000")).thenReturn(false);

        assertThatThrownBy(() -> service.confirmSetup(u.getId().toString(), "000000"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Invalid authentication code");
        verify(userRepo, never()).save(any());
    }

    @Test
    void confirmSetup_validCode_enablesAndReturnsTenRecoveryCodes() {
        User u = user();
        u.setTotpSecret("SECRET");
        when(userRepo.findById(u.getId())).thenReturn(Optional.of(u));
        when(codeVerifier.isValidCode("SECRET", "123456")).thenReturn(true);
        when(passwordEncoder.encode(anyString())).thenAnswer(inv -> "hashed-" + inv.getArgument(0));

        TwoFactorService.EnableResult result = service.confirmSetup(u.getId().toString(), "123456");

        assertThat(u.getTotpEnabled()).isTrue();
        assertThat(u.getTotpRecoveryCodes()).hasSize(10);
        // All hashed (we substituted "hashed-" prefix above).
        assertThat(u.getTotpRecoveryCodes()).allMatch(c -> c.startsWith("hashed-"));
        assertThat(result.recoveryCodes()).hasSize(10);
        // Plaintext format: 5 chars, dash, 5 chars from the recovery alphabet.
        assertThat(result.recoveryCodes()).allMatch(c -> c.matches("[A-Z0-9]{5}-[A-Z0-9]{5}"));
        // Plaintext returned to caller must NOT include any letters from the
        // alphabet's exclusions (I, L, O, 0, 1) — those are deliberately
        // omitted from RECOVERY_ALPHABET to avoid visual ambiguity.
        assertThat(result.recoveryCodes()).noneMatch(c -> c.replace("-", "").matches(".*[ILO01].*"));
        verify(userRepo).save(u);
    }

    // ── disable ───────────────────────────────────────────────────────

    @Test
    void disable_unknownUser_throws() {
        UUID id = UUID.randomUUID();
        when(userRepo.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.disable(id.toString(), "anything"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void disable_notEnabled_throws() {
        User u = user();
        u.setTotpEnabled(false);
        when(userRepo.findById(u.getId())).thenReturn(Optional.of(u));

        assertThatThrownBy(() -> service.disable(u.getId().toString(), "x"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("not currently enabled");
    }

    @Test
    void disable_passwordMatch_clearsTotpFields() {
        User u = user();
        u.setTotpEnabled(true);
        u.setTotpSecret("SECRET");
        u.setTotpRecoveryCodes(new ArrayList<>(List.of("h1", "h2")));
        when(userRepo.findById(u.getId())).thenReturn(Optional.of(u));
        when(passwordEncoder.matches("plaintext-pw", "encoded-pw")).thenReturn(true);

        service.disable(u.getId().toString(), "plaintext-pw");

        assertThat(u.getTotpEnabled()).isFalse();
        assertThat(u.getTotpSecret()).isNull();
        assertThat(u.getTotpRecoveryCodes()).isNull();
        verify(userRepo).save(u);
    }

    @Test
    void disable_totpCodeMatch_clearsTotpFields() {
        User u = user();
        u.setTotpEnabled(true);
        u.setTotpSecret("SECRET");
        when(userRepo.findById(u.getId())).thenReturn(Optional.of(u));
        when(passwordEncoder.matches("123456", "encoded-pw")).thenReturn(false);
        when(codeVerifier.isValidCode("SECRET", "123456")).thenReturn(true);

        service.disable(u.getId().toString(), "123456");

        assertThat(u.getTotpEnabled()).isFalse();
        assertThat(u.getTotpSecret()).isNull();
    }

    @Test
    void disable_neitherMatches_throws() {
        User u = user();
        u.setTotpEnabled(true);
        u.setTotpSecret("SECRET");
        when(userRepo.findById(u.getId())).thenReturn(Optional.of(u));
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(false);
        when(codeVerifier.isValidCode(anyString(), anyString())).thenReturn(false);

        assertThatThrownBy(() -> service.disable(u.getId().toString(), "wrong"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Invalid password");
        // The user's 2FA must remain intact — failing verification must NOT
        // partially clear state.
        assertThat(u.getTotpEnabled()).isTrue();
        assertThat(u.getTotpSecret()).isEqualTo("SECRET");
        verify(userRepo, never()).save(any());
    }

    @Test
    void disable_userWithBlankPassword_fallsThroughToTotpCheck() {
        // OAuth-only users have no local password; the password branch must
        // be skipped (not call passwordEncoder.matches with a blank hash).
        User u = user();
        u.setPassword("");
        u.setTotpEnabled(true);
        u.setTotpSecret("SECRET");
        when(userRepo.findById(u.getId())).thenReturn(Optional.of(u));
        when(codeVerifier.isValidCode("SECRET", "123456")).thenReturn(true);

        service.disable(u.getId().toString(), "123456");

        assertThat(u.getTotpEnabled()).isFalse();
        verify(passwordEncoder, never()).matches(anyString(), eq(""));
    }

    // ── verifyDuringSignin ────────────────────────────────────────────

    @Test
    void verifyDuringSignin_validTotp_returnsTrue_doesNotConsume() {
        User u = user();
        u.setTotpSecret("SECRET");
        u.setTotpRecoveryCodes(new ArrayList<>(List.of("h1", "h2")));
        when(codeVerifier.isValidCode("SECRET", "123456")).thenReturn(true);

        assertThat(service.verifyDuringSignin(u, "123456")).isTrue();

        // Recovery list untouched on TOTP-path success.
        assertThat(u.getTotpRecoveryCodes()).hasSize(2);
        verify(userRepo, never()).save(any());
    }

    @Test
    void verifyDuringSignin_recoveryCodeMatch_consumesAndReturnsTrue() {
        User u = user();
        u.setTotpSecret("SECRET");
        u.setTotpRecoveryCodes(new ArrayList<>(List.of("h1", "h2", "h3")));
        when(codeVerifier.isValidCode(anyString(), anyString())).thenReturn(false);
        when(passwordEncoder.matches("RECOVERY", "h1")).thenReturn(false);
        when(passwordEncoder.matches("RECOVERY", "h2")).thenReturn(true);

        assertThat(service.verifyDuringSignin(u, "RECOVERY")).isTrue();

        // h2 consumed; h1 and h3 remain.
        assertThat(u.getTotpRecoveryCodes()).containsExactly("h1", "h3");
        verify(userRepo).save(u);
    }

    @Test
    void verifyDuringSignin_noMatchAtAll_returnsFalse() {
        User u = user();
        u.setTotpSecret("SECRET");
        u.setTotpRecoveryCodes(new ArrayList<>(List.of("h1")));
        when(codeVerifier.isValidCode(anyString(), anyString())).thenReturn(false);
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(false);

        assertThat(service.verifyDuringSignin(u, "wrong")).isFalse();
        verify(userRepo, never()).save(any());
    }

    @Test
    void verifyDuringSignin_nullSecretAndNoRecoveryCodes_returnsFalse() {
        User u = user();
        u.setTotpSecret(null);
        u.setTotpRecoveryCodes(null);

        assertThat(service.verifyDuringSignin(u, "anything")).isFalse();
        verify(userRepo, never()).save(any());
    }

    @Test
    void verifyDuringSignin_emptyRecoveryCodes_returnsFalse() {
        User u = user();
        u.setTotpSecret("SECRET");
        u.setTotpRecoveryCodes(new ArrayList<>());
        when(codeVerifier.isValidCode("SECRET", "wrong")).thenReturn(false);

        assertThat(service.verifyDuringSignin(u, "wrong")).isFalse();
    }
}
