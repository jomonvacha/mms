package com.roots.mms.controller;

import com.roots.mms.dto.request.UpdateUserProfileRequest;
import com.roots.mms.dto.request.UpdateUserRolesRequest;
import com.roots.mms.dto.request.UpdateUserStatusRequest;
import com.roots.mms.dto.response.MemberResponse;
import com.roots.mms.dto.response.UserResponse;
import com.roots.mms.exception.BusinessRuleException;
import com.roots.mms.repository.UserRepository;
import com.roots.mms.service.MemberService;
import com.roots.mms.service.UserService;
import com.roots.mms.service.VerificationTokenService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@Slf4j
@RequiredArgsConstructor
@RequestMapping("/api/admin/users")
public class AdminUserController {

  private final UserService userService;
  private final MemberService memberService;
  private final VerificationTokenService verificationTokenService;
  private final UserRepository userRepository;

  @GetMapping
  @PreAuthorize("hasRole('ADMIN') or hasRole('MODERATOR') or hasRole('MANAGER')")
  public ResponseEntity<Page<UserResponse>> listUsers(
    @RequestParam(defaultValue = "0") int page,
    @RequestParam(defaultValue = "10") int size,
    @RequestParam(defaultValue = "id") String sortBy,
    @RequestParam(defaultValue = "asc") String sortDir) {
    log.debug("[AdminUser] List users page={} size={} sortBy={} sortDir={}", page, size, sortBy, sortDir);
    return ResponseEntity.ok(userService.listUsers(page, size, sortBy, sortDir));
  }

  @GetMapping("/{id}")
  @PreAuthorize("hasRole('ADMIN') or hasRole('MODERATOR') or hasRole('MANAGER')")
  public ResponseEntity<UserResponse> getUser(@PathVariable String id) {
    log.debug("[AdminUser] Get user id={}", id);
    return ResponseEntity.ok(userService.getUserById(id));
  }

  @PutMapping("/{id}/profile")
  @PreAuthorize("hasRole('ADMIN')")
  public ResponseEntity<UserResponse> updateProfile(@PathVariable String id,
                                                    @Valid @RequestBody UpdateUserProfileRequest request) {
    log.info("[AdminUser] Update profile for id={}", id);
    return ResponseEntity.ok(userService.updateProfile(id, request));
  }

  @PutMapping("/{id}/roles")
  @PreAuthorize("hasRole('ADMIN')")
  public ResponseEntity<UserResponse> updateRoles(@PathVariable String id,
                                                  @Valid @RequestBody UpdateUserRolesRequest request) {
    log.info("[AdminUser] Update roles for id={} roles={}", id, request.getRoles());
    return ResponseEntity.ok(userService.updateRoles(id, request));
  }

  @PutMapping("/{id}/status")
  @PreAuthorize("hasRole('ADMIN')")
  public ResponseEntity<UserResponse> updateStatus(@PathVariable String id,
                                                   @Valid @RequestBody UpdateUserStatusRequest request) {
    log.info("[AdminUser] Update status for id={} active={}", id, request.getActive());
    return ResponseEntity.ok(userService.updateStatus(id, request));
  }

  @PutMapping("/{id}/provider")
  @PreAuthorize("hasRole('ADMIN')")
  public ResponseEntity<UserResponse> updateProvider(@PathVariable String id,
                                                     @RequestBody java.util.Map<String, String> body) {
    String provider = body.get("provider");
    log.info("[AdminUser] Update provider for id={} provider={}", id, provider);
    return ResponseEntity.ok(userService.updateProvider(id, provider));
  }

  @PutMapping("/{id}/password")
  @PreAuthorize("hasRole('ADMIN')")
  public ResponseEntity<com.roots.mms.dto.response.MessageResponse> setPassword(
          @PathVariable String id,
          @RequestBody java.util.Map<String, String> body) {
    String newPassword = body.get("newPassword");
    if (newPassword == null || newPassword.isBlank()) {
      throw new BusinessRuleException("newPassword is required");
    }
    log.info("[AdminUser] Set password for id={}", id);
    userService.adminSetPassword(id, newPassword);
    return ResponseEntity.ok(new com.roots.mms.dto.response.MessageResponse("Password updated"));
  }

  @PostMapping("/{id}/send-reset-link")
  @PreAuthorize("hasRole('ADMIN')")
  public ResponseEntity<com.roots.mms.dto.response.MessageResponse> sendResetLink(@PathVariable String id) {
    var user = userRepository.findById(java.util.UUID.fromString(id))
            .orElseThrow(() -> new com.roots.mms.exception.ResourceNotFoundException("User", "id", id));
    log.info("[AdminUser] Send reset link for id={} email={}", id, user.getEmail());
    verificationTokenService.startPasswordReset(user.getEmail());
    return ResponseEntity.ok(new com.roots.mms.dto.response.MessageResponse("Password reset link sent"));
  }

  @PutMapping("/{id}/membership")
  @PreAuthorize("hasRole('ADMIN')")
  public ResponseEntity<MemberResponse> updateMembership(@PathVariable String id,
                                                         @RequestBody java.util.Map<String, String> body) {
    String categoryCode = body.get("categoryCode");
    String tierCode = body.get("tierCode");
    if (categoryCode == null || tierCode == null) {
      throw new BusinessRuleException("categoryCode and tierCode are required");
    }
    log.info("[AdminUser] Update membership for id={} category={} tier={}", id, categoryCode, tierCode);
    return ResponseEntity.ok(memberService.selectTierForUser(id, categoryCode, tierCode));
  }

  @DeleteMapping("/{id}")
  @PreAuthorize("hasRole('ADMIN')")
  public ResponseEntity<com.roots.mms.dto.response.MessageResponse> deleteUser(@PathVariable String id) {
    log.warn("[AdminUser] Delete user id={} by principalId={}", id, com.roots.mms.security.SecurityUtils.getCurrentUserIdOrNull());
    userService.deleteUser(id);
    return ResponseEntity.ok(new com.roots.mms.dto.response.MessageResponse("User deleted"));
  }
}
