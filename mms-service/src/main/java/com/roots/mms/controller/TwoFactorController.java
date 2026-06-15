package com.roots.mms.controller;

import com.roots.mms.security.SecurityUtils;
import com.roots.mms.service.TwoFactorService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Two-factor authentication endpoints (TOTP authenticator apps). All require
 * an authenticated user — operate on the caller's own account.
 *
 * <p>Flow:
 * <ol>
 *   <li>{@code POST /setup} — server returns a fresh secret + otpauth URI to
 *       render as a QR code in the client.</li>
 *   <li>User scans the QR with their authenticator, enters the current 6-digit
 *       code.</li>
 *   <li>{@code POST /enable} — client submits the code; server verifies it,
 *       enables 2FA, and returns one-time recovery codes (shown exactly once).</li>
 *   <li>{@code POST /disable} — client submits either the current password or
 *       a valid TOTP code to disable 2FA.</li>
 * </ol>
 */
@RestController
@RequestMapping("/api/users/me/2fa")
@PreAuthorize("isAuthenticated()")
@RequiredArgsConstructor
@Slf4j
public class TwoFactorController {

    private final TwoFactorService twoFactorService;

    @Data
    public static class CodeRequest {
        @NotBlank
        private String code;
    }

    @Data
    public static class DisableRequest {
        @NotBlank
        private String currentPasswordOrCode;
    }

    @PostMapping("/setup")
    public ResponseEntity<TwoFactorService.SetupResult> setup() {
        String userId = SecurityUtils.getCurrentUserIdOrThrow();
        return ResponseEntity.ok(twoFactorService.startSetup(userId));
    }

    @PostMapping("/enable")
    public ResponseEntity<TwoFactorService.EnableResult> enable(@Valid @RequestBody CodeRequest req) {
        String userId = SecurityUtils.getCurrentUserIdOrThrow();
        return ResponseEntity.ok(twoFactorService.confirmSetup(userId, req.getCode()));
    }

    @PostMapping("/disable")
    public ResponseEntity<Void> disable(@Valid @RequestBody DisableRequest req) {
        String userId = SecurityUtils.getCurrentUserIdOrThrow();
        twoFactorService.disable(userId, req.getCurrentPasswordOrCode());
        return ResponseEntity.noContent().build();
    }

    /**
     * Mints a fresh batch of one-time recovery codes (replacing any unused ones)
     * for a user who already has 2FA enabled. Returned exactly once.
     */
    @PostMapping("/recovery-codes")
    public ResponseEntity<TwoFactorService.EnableResult> regenerateRecoveryCodes() {
        String userId = SecurityUtils.getCurrentUserIdOrThrow();
        return ResponseEntity.ok(twoFactorService.regenerateRecoveryCodes(userId));
    }
}
