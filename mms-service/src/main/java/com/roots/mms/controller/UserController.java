package com.roots.mms.controller;

import com.roots.mms.dto.request.AccountDeletionRequest;
import com.roots.mms.dto.request.ChangeEmailRequest;
import com.roots.mms.dto.request.ChangePasswordRequest;
import com.roots.mms.dto.request.SetPasswordRequest;
import com.roots.mms.dto.request.UpdateUserProfileRequest;
import com.roots.mms.dto.response.UserResponse;
import com.roots.mms.dto.response.MessageResponse;
import com.roots.mms.dto.response.UserResponse;
import com.roots.mms.entity.UserAvatar;
import com.roots.mms.exception.AuthorizationException;
import com.roots.mms.repository.UserAvatarRepository;
import com.roots.mms.security.SecurityUtils;
import com.roots.mms.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@Slf4j
@RequiredArgsConstructor
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;
    private final UserAvatarRepository userAvatarRepository;

    @org.springframework.beans.factory.annotation.Value("${app.account.deletion-grace-days:30}")
    private int deletionGraceDays;

    /** Explicit avatar limits (surfaced to the UI as upload hints). */
    private static final long MAX_AVATAR_BYTES = 2L * 1024 * 1024; // 2 MB
    private static final java.util.Set<String> ALLOWED_AVATAR_TYPES =
            java.util.Set.of("image/jpeg", "image/png", "image/gif", "image/webp");

    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<UserResponse> getMe() {
        String userId = SecurityUtils.getCurrentUserIdOrNull();
        if (userId == null) throw new AuthorizationException("User", "read_self");
        return ResponseEntity.ok(userService.getUserById(userId));
    }

    @PutMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<UserResponse> updateMe(@Valid @RequestBody UpdateUserProfileRequest request) {
        String userId = SecurityUtils.getCurrentUserIdOrNull();
        if (userId == null) throw new AuthorizationException("User", "update_self");
        return ResponseEntity.ok(userService.updateProfile(userId, request));
    }

    @PutMapping("/me/password")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<MessageResponse> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        String userId = SecurityUtils.getCurrentUserIdOrNull();
        if (userId == null) throw new AuthorizationException("User", "change_password");
        userService.changePassword(userId, request);
        return ResponseEntity.ok(new MessageResponse("Password changed successfully"));
    }

    /**
     * Disconnects a federated (Google/Apple) account from the identity provider
     * and converts it to a local account. The caller must set a local password
     * as part of this operation so they can still sign in afterwards.
     *
     * <p>After this call, the user is treated identically to any local user:
     * they can change their password, change their email, etc.
     */
    /**
     * Starts a verified email change. Sends a confirmation link to the new
     * address and a heads-up to the current address; the email only changes
     * after the new address is confirmed via
     * {@code GET /api/auth/confirm-email-change}.
     */
    @PostMapping("/me/email-change/request")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<MessageResponse> requestEmailChange(@Valid @RequestBody ChangeEmailRequest request) {
        String userId = SecurityUtils.getCurrentUserIdOrNull();
        if (userId == null) throw new AuthorizationException("User", "change_email");
        userService.requestEmailChange(userId, request.getNewEmail(), request.getCurrentPassword());
        return ResponseEntity.ok(new MessageResponse(
                "Confirmation sent to your new address. The change takes effect once you confirm it."));
    }

    /**
     * Schedules self-service account deletion after a reversible grace window.
     * The account stays usable (so the user can cancel) until it is purged.
     */
    @PostMapping("/me/deletion")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<UserResponse> requestDeletion(@RequestBody(required = false) AccountDeletionRequest request) {
        String userId = SecurityUtils.getCurrentUserIdOrNull();
        if (userId == null) throw new AuthorizationException("User", "request_deletion");
        String pw = request != null ? request.getCurrentPassword() : null;
        return ResponseEntity.ok(userService.requestDeletion(userId, pw, deletionGraceDays));
    }

    /** Cancels (halts) a pending account deletion. */
    @DeleteMapping("/me/deletion")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<UserResponse> cancelDeletion() {
        String userId = SecurityUtils.getCurrentUserIdOrNull();
        if (userId == null) throw new AuthorizationException("User", "cancel_deletion");
        return ResponseEntity.ok(userService.cancelDeletion(userId));
    }

    @PostMapping("/me/disconnect-provider")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<UserResponse> disconnectProvider(@Valid @RequestBody SetPasswordRequest request) {
        String userId = SecurityUtils.getCurrentUserIdOrNull();
        if (userId == null) throw new AuthorizationException("User", "disconnect_provider");
        UserResponse updated = userService.disconnectProvider(userId, request.getNewPassword());
        return ResponseEntity.ok(updated);
    }

    @GetMapping("/me/avatar")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<byte[]> getMyAvatar() {
        String userId = SecurityUtils.getCurrentUserIdOrNull();
        if (userId == null) throw new AuthorizationException("User", "read_avatar");
        return userAvatarRepository.findById(java.util.UUID.fromString(userId))
                .map(a -> ResponseEntity.ok()
                        .contentType(MediaType.parseMediaType(
                                a.getContentType() != null ? a.getContentType() : MediaType.APPLICATION_OCTET_STREAM_VALUE))
                        .body(a.getData()))
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping(value = "/me/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<MessageResponse> uploadMyAvatar(@RequestParam("file") MultipartFile file) {
        String userId = SecurityUtils.getCurrentUserIdOrNull();
        if (userId == null) throw new AuthorizationException("User", "upload_avatar");
        if (file.isEmpty()) return ResponseEntity.badRequest().body(new MessageResponse("No file uploaded"));
        String contentType = file.getContentType();
        String ct = contentType == null ? "" : contentType.toLowerCase();
        if (!ALLOWED_AVATAR_TYPES.contains(ct)) {
            return ResponseEntity.badRequest().body(new MessageResponse(
                    "Unsupported image type. Allowed: JPG, PNG, GIF, WebP."));
        }
        if (file.getSize() > MAX_AVATAR_BYTES) {
            return ResponseEntity.status(413).body(new MessageResponse("Image is too large (max 2 MB)"));
        }
        try {
            java.util.UUID userUuid = java.util.UUID.fromString(userId);
            UserAvatar avatar = userAvatarRepository.findById(userUuid).orElseGet(UserAvatar::new);
            avatar.setUserId(userUuid);
            avatar.setContentType(contentType);
            avatar.setData(file.getBytes());
            userAvatarRepository.save(avatar);
            return ResponseEntity.ok(new MessageResponse("Avatar uploaded"));
        } catch (Exception e) {
            log.error("Avatar upload failed for userId={}", userId, e);
            return ResponseEntity.internalServerError().body(new MessageResponse("Failed to upload avatar"));
        }
    }
}
