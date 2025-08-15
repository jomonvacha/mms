package com.roots.mms.controller;

import com.roots.mms.dto.request.ChangePasswordRequest;
import com.roots.mms.dto.request.UpdateUserProfileRequest;
import com.roots.mms.dto.response.MessageResponse;
import com.roots.mms.dto.response.UserResponse;
import com.roots.mms.exception.AuthorizationException;
import com.roots.mms.security.SecurityUtils;
import com.roots.mms.service.UserService;
import com.roots.mms.repository.UserAvatarRepository;
import com.roots.mms.repository.UserRepository;
import com.roots.mms.entity.UserAvatar;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@CrossOrigin(origins = "*", maxAge = 3600)
@RestController
@Slf4j
@RequiredArgsConstructor
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;
    private final UserAvatarRepository userAvatarRepository;
    private final UserRepository userRepository;

  @GetMapping("/me")
  @PreAuthorize("hasRole('USER') or hasRole('MODERATOR') or hasRole('ADMIN') or hasRole('MANAGER') or hasRole('MEMBER')")
  public ResponseEntity<UserResponse> getMe() {
    Long userId = SecurityUtils.getCurrentUserIdOrNull();
    if (userId == null) throw new AuthorizationException("User", "read_self");
    log.debug("[User] Get profile for id={}", userId);
    return ResponseEntity.ok(userService.getUserById(userId));
  }

  @PutMapping("/me")
  @PreAuthorize("hasRole('USER') or hasRole('MODERATOR') or hasRole('ADMIN') or hasRole('MANAGER') or hasRole('MEMBER')")
  public ResponseEntity<UserResponse> updateMe(@Valid @RequestBody UpdateUserProfileRequest request) {
    Long userId = SecurityUtils.getCurrentUserIdOrNull();
    if (userId == null) throw new AuthorizationException("User", "update_self");
    log.info("[User] Update profile for id={}", userId);
    return ResponseEntity.ok(userService.updateProfile(userId, request));
  }

  @PutMapping("/me/password")
  @PreAuthorize("hasRole('USER') or hasRole('MODERATOR') or hasRole('ADMIN') or hasRole('MANAGER') or hasRole('MEMBER')")
    public ResponseEntity<MessageResponse> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
    Long userId = SecurityUtils.getCurrentUserIdOrNull();
    if (userId == null) throw new AuthorizationException("User", "change_password");
    log.info("[User] Change password for id={}", userId);
    userService.changePassword(userId, request);
        return ResponseEntity.ok(new MessageResponse("Password changed successfully"));
    }

    @GetMapping("/me/avatar")
    @PreAuthorize("hasRole('USER') or hasRole('MODERATOR') or hasRole('ADMIN') or hasRole('MANAGER') or hasRole('MEMBER')")
    public ResponseEntity<byte[]> getMyAvatar() {
        Long userId = SecurityUtils.getCurrentUserIdOrNull();
        if (userId == null) throw new AuthorizationException("User", "read_avatar");
        return userAvatarRepository.findById(userId)
                .map(a -> ResponseEntity.ok()
                        .contentType(MediaType.parseMediaType(a.getContentType() == null ? MediaType.APPLICATION_OCTET_STREAM_VALUE : a.getContentType()))
                        .body(a.getData()))
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping(value = "/me/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('USER') or hasRole('MODERATOR') or hasRole('ADMIN') or hasRole('MANAGER') or hasRole('MEMBER')")
    @org.springframework.transaction.annotation.Transactional
    public ResponseEntity<MessageResponse> uploadMyAvatar(@RequestParam("file") MultipartFile file) {
        Long userId = SecurityUtils.getCurrentUserIdOrNull();
        if (userId == null) throw new AuthorizationException("User", "upload_avatar");
        if (file.isEmpty()) return ResponseEntity.badRequest().body(new MessageResponse("No file uploaded"));
        String contentType = file.getContentType();
        if (contentType == null || !contentType.toLowerCase().startsWith("image/")) {
            return ResponseEntity.badRequest().body(new MessageResponse("Only image files are allowed"));
        }
        if (file.getSize() > 5 * 1024 * 1024) {
            return ResponseEntity.status(413).body(new MessageResponse("File is too large (max 5MB)"));
        }
        try {
            var user = userRepository.findById(userId)
                    .orElseThrow(() -> new AuthorizationException("User", "upload_avatar_no_user"));
            UserAvatar avatar = userAvatarRepository.findById(userId).orElseGet(UserAvatar::new);
            avatar.setUser(user); // @MapsId will set PK
            avatar.setContentType(contentType);
            avatar.setData(file.getBytes());
            userAvatarRepository.save(avatar);
            return ResponseEntity.ok(new MessageResponse("Avatar uploaded"));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(new MessageResponse("Failed to upload avatar"));
        }
    }
}
