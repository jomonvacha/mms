package com.roots.mms.service;

import com.roots.mms.dto.request.ChangePasswordRequest;
import com.roots.mms.dto.request.UpdateUserProfileRequest;
import com.roots.mms.dto.request.UpdateUserRolesRequest;
import com.roots.mms.dto.request.UpdateUserStatusRequest;
import com.roots.mms.dto.response.UserResponse;
import com.roots.mms.entity.AuthProvider;
import com.roots.mms.entity.ERole;
import com.roots.mms.entity.Role;
import com.roots.mms.entity.User;
import com.roots.mms.exception.BusinessRuleException;
import com.roots.mms.exception.ResourceNotFoundException;
import com.roots.mms.repository.RoleRepository;
import com.roots.mms.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final VerificationTokenService verificationTokenService;

    @org.springframework.beans.factory.annotation.Value("${app.account.username-change-cooldown-days:30}")
    private int usernameCooldownDays;

    public UserResponse getUserById(String id) {
        User user = userRepository.findById(java.util.UUID.fromString(id))
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", id));
        return toResponse(user);
    }

    public UserResponse updateProfile(String id, UpdateUserProfileRequest request) {
        User user = userRepository.findById(java.util.UUID.fromString(id))
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", id));
        if (request.getEmail() != null) {
            String newEmail = request.getEmail();
            if (!newEmail.equalsIgnoreCase(user.getEmail())) {
                // Email is NOT changed via the profile PUT — that would be a silent
                // swap. It must go through the verified change-email flow
                // (POST /api/users/me/email-change/request) which confirms the new
                // address and notifies the old one.
                throw new BusinessRuleException(
                        "Email cannot be changed here. Use the verified change-email flow.");
            }
        }
        if (request.getUsername() != null) {
            String newUsername = request.getUsername().trim();
            if (!newUsername.equals(user.getUsername())) {
                if (!newUsername.matches("^[A-Za-z0-9_]{3,20}$")) {
                    throw new BusinessRuleException(
                            "Username must be 3–20 characters: letters, numbers, and underscores only");
                }
                java.time.LocalDateTime changedAt = user.getUsernameChangedAt();
                if (changedAt != null) {
                    java.time.LocalDateTime nextAllowed = changedAt.plusDays(usernameCooldownDays);
                    if (java.time.LocalDateTime.now().isBefore(nextAllowed)) {
                        long days = Math.max(1, java.time.Duration.between(
                                java.time.LocalDateTime.now(), nextAllowed).toDays());
                        throw new BusinessRuleException(
                                "Username was changed recently. You can change it again in " + days + " day(s).");
                    }
                }
                if (userRepository.existsByUsername(newUsername)) {
                    throw new BusinessRuleException("That username is already taken.");
                }
                user.setUsername(newUsername);
                user.setUsernameChangedAt(java.time.LocalDateTime.now());
            }
        }
        if (request.getFirstName() != null) {
            String fn = request.getFirstName().trim();
            if (fn.isEmpty()) throw new BusinessRuleException("First name cannot be empty");
            user.setFirstName(fn);
        }
        if (request.getLastName() != null) {
            String ln = request.getLastName().trim();
            if (ln.isEmpty()) throw new BusinessRuleException("Last name cannot be empty");
            user.setLastName(ln);
        }
        if (request.getPhoneNumber() != null) {
            String phone = request.getPhoneNumber().trim();
            if (!phone.isEmpty() && !phone.matches("^\\+?[0-9.\\-\\s()]{7,20}$")) {
                throw new BusinessRuleException("Invalid phone number format");
            }
            user.setPhoneNumber(phone.isEmpty() ? null : phone);
        }
        userRepository.save(user);
        return toResponse(user);
    }

    /**
     * Starts a verified email-change for the signed-in user. Validates the
     * caller's password, blocks federated accounts (their email is owned by the
     * IdP), and rejects already-taken addresses, then dispatches the
     * confirmation + notice emails. The address only changes once confirmed.
     */
    public void requestEmailChange(String id, String newEmail, String currentPassword) {
        User user = userRepository.findById(java.util.UUID.fromString(id))
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", id));
        if (isFederated(user)) {
            throw new BusinessRuleException(
                    "Email is managed by " + providerLabel(user) + " and cannot be changed here. "
                            + "Update it with your identity provider.");
        }
        if (user.getPassword() == null || user.getPassword().isBlank()
                || !passwordEncoder.matches(currentPassword, user.getPassword())) {
            throw new BusinessRuleException("Current password is incorrect");
        }
        String normalized = newEmail == null ? "" : newEmail.trim();
        if (normalized.isEmpty()) {
            throw new BusinessRuleException("New email is required");
        }
        if (normalized.equalsIgnoreCase(user.getEmail())) {
            throw new BusinessRuleException("That is already your email address");
        }
        if (userRepository.existsByEmail(normalized)) {
            // Non-enumerating message — same wording the signup flow uses.
            throw new BusinessRuleException("That email address is not available.");
        }
        verificationTokenService.startEmailChange(user, normalized);
    }

    /**
     * Admin-initiated password set. Skips the current-password check (admins don't
     * know it), but still enforces complexity rules and blocks for federated users.
     */
    public void adminSetPassword(String id, String newPassword) {
        User user = userRepository.findById(java.util.UUID.fromString(id))
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", id));
        if (isFederated(user)) {
            throw new BusinessRuleException(
                    "Password is managed by " + providerLabel(user) + ". "
                            + "This user signs in via " + providerLabel(user) + ".");
        }
        if (newPassword == null || newPassword.length() < 8
                || !newPassword.matches(".*[A-Za-z].*")
                || !newPassword.matches(".*[0-9].*")) {
            throw new BusinessRuleException("Password must be at least 8 characters and include letters and numbers");
        }
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
    }

    public void changePassword(String id, ChangePasswordRequest request) {
        User user = userRepository.findById(java.util.UUID.fromString(id))
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", id));
        // Federated accounts have no local password — block the flow with a clear message
        // instead of letting BCrypt fail on a null/blank stored password.
        if (isFederated(user) || user.getPassword() == null || user.getPassword().isBlank()) {
            throw new BusinessRuleException(
                    "Password is managed by " + providerLabel(user) + ". "
                            + "Sign in via " + providerLabel(user) + " to change it.");
        }
        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())) {
            throw new BusinessRuleException("Current password is incorrect");
        }
        String newPw = request.getNewPassword();
        if (newPw == null || newPw.length() < 8 || !newPw.matches(".*[A-Za-z].*") || !newPw.matches(".*[0-9].*")) {
            throw new BusinessRuleException("New password must be at least 8 characters and include letters and numbers");
        }
        if (passwordEncoder.matches(newPw, user.getPassword())) {
            throw new BusinessRuleException("New password must be different from current password");
        }
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
    }

    public Page<UserResponse> listUsers(int page, int size, String sortBy, String sortDir) {
        Sort sort = sortDir.equalsIgnoreCase("desc") ? Sort.by(sortBy).descending() : Sort.by(sortBy).ascending();
        Pageable pageable = PageRequest.of(page, size, sort);
        return userRepository.findAll(pageable).map(this::toResponse);
    }

    public UserResponse updateRoles(String id, UpdateUserRolesRequest request) {
        User user = userRepository.findById(java.util.UUID.fromString(id))
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", id));
        Set<Role> newRoles = new HashSet<>();
        for (String roleName : request.getRoles()) {
            Role role = roleRepository.findByName(roleName)
                    .orElseThrow(() -> new ResourceNotFoundException("Role", "name", roleName));
            newRoles.add(role);
        }
        user.setRoles(newRoles);
        userRepository.save(user);
        return toResponse(user);
    }

    public UserResponse updateStatus(String id, UpdateUserStatusRequest request) {
        User user = userRepository.findById(java.util.UUID.fromString(id))
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", id));
        user.setActive(request.getActive());
        userRepository.save(user);
        return toResponse(user);
    }

    public void deleteUser(String id) {
        User user = userRepository.findById(java.util.UUID.fromString(id))
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", id));
        userRepository.delete(user);
    }

    /**
     * Schedules self-service account deletion after a reversible grace window.
     * Re-authenticates local users with their password; federated users are
     * already proven by their OAuth session. The account is only purged after
     * the window elapses (by {@code AccountDeletionJob}) and can be cancelled
     * via {@link #cancelDeletion(String)} at any point before then.
     */
    public UserResponse requestDeletion(String id, String currentPassword, int graceDays) {
        User user = userRepository.findById(java.util.UUID.fromString(id))
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", id));
        boolean hasPassword = user.getPassword() != null && !user.getPassword().isBlank();
        if (hasPassword && !passwordEncoder.matches(currentPassword, user.getPassword())) {
            throw new BusinessRuleException("Current password is incorrect");
        }
        user.setPendingDeletion(true);
        user.setDeletionScheduledAt(java.time.LocalDateTime.now().plusDays(graceDays));
        userRepository.save(user);
        return toResponse(user);
    }

    /** Halts a pending deletion, restoring the account to normal. */
    public UserResponse cancelDeletion(String id) {
        User user = userRepository.findById(java.util.UUID.fromString(id))
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", id));
        if (!Boolean.TRUE.equals(user.getPendingDeletion())) {
            throw new BusinessRuleException("No account deletion is currently scheduled.");
        }
        user.setPendingDeletion(false);
        user.setDeletionScheduledAt(null);
        userRepository.save(user);
        return toResponse(user);
    }

    public UserResponse updateProvider(String id, String providerName) {
        User user = userRepository.findById(java.util.UUID.fromString(id))
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", id));
        AuthProvider p;
        try { p = AuthProvider.valueOf(providerName); } catch (Exception e) {
            throw new BusinessRuleException("The specified authentication provider is not supported.");
        }
        user.setProvider(p);
        if (p == AuthProvider.LOCAL) user.setProviderId(null);
        userRepository.save(user);
        return toResponse(user);
    }

    public UserResponse toResponse(User user) {
        UserResponse response = new UserResponse();
        response.setId(user.getId() != null ? user.getId().toString() : null);
        response.setUsername(user.getUsername());
        response.setEmail(user.getEmail());
        response.setFirstName(user.getFirstName());
        response.setLastName(user.getLastName());
        response.setPhoneNumber(user.getPhoneNumber());
        response.setActive(user.getActive());
        response.setCreatedAt(user.getCreatedAt());
        response.setUpdatedAt(user.getUpdatedAt());
        response.setRoles(user.getRoles().stream().map(Role::getName).collect(Collectors.toList()));
        response.setHasPassword(user.getPassword() != null && !user.getPassword().isBlank());
        response.setProvider(user.getProvider() != null ? user.getProvider().name() : AuthProvider.LOCAL.name());
        response.setEmailVerified(Boolean.TRUE.equals(user.getEmailVerified()));
        response.setTwoFactorEnabled(Boolean.TRUE.equals(user.getTotpEnabled()));
        response.setPendingDeletion(Boolean.TRUE.equals(user.getPendingDeletion()));
        response.setDeletionScheduledAt(user.getDeletionScheduledAt());
        return response;
    }

    /**
     * Disconnects a federated (Google/Apple) account from the identity provider
     * and converts it to a local account. The user must provide a new local
     * password as part of the disconnect — otherwise they'd be locked out.
     *
     * <p>After this call the user's {@code provider} is {@code LOCAL},
     * {@code providerId} is cleared, and they can sign in with username/password.
     * All subsequent account-management flows (change password, change email)
     * are unlocked.
     *
     * @throws BusinessRuleException if the account is already LOCAL
     */
    public UserResponse disconnectProvider(String id, String newPassword) {
        User user = userRepository.findById(java.util.UUID.fromString(id))
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", id));
        if (!isFederated(user)) {
            throw new BusinessRuleException("This account is not connected to an external provider.");
        }
        boolean alreadyHasPassword = user.getPassword() != null && !user.getPassword().isBlank();
        if (!alreadyHasPassword) {
            // User has no local password — they MUST set one or they'll be locked out
            if (newPassword == null || newPassword.length() < 8
                    || !newPassword.matches(".*[A-Za-z].*") || !newPassword.matches(".*\\d.*")) {
                throw new BusinessRuleException(
                        "Password must be 8–64 characters with at least one letter and one number");
            }
            user.setPassword(passwordEncoder.encode(newPassword));
        }
        // If they already have a password, just flip the provider — no new password needed.
        user.setProvider(AuthProvider.LOCAL);
        user.setProviderId(null);
        userRepository.save(user);
        return toResponse(user);
    }

    private static boolean isFederated(User user) {
        return user.getProvider() != null && user.getProvider() != AuthProvider.LOCAL;
    }

    private static String providerLabel(User user) {
        if (user.getProvider() == null) return "your identity provider";
        return switch (user.getProvider()) {
            case GOOGLE -> "Google";
            case APPLE -> "Apple";
            case LOCAL -> "your identity provider";
        };
    }
}
