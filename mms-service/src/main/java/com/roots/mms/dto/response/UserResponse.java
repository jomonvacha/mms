package com.roots.mms.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserResponse {
    private String id;
    private String username;
    private String email;
    private String firstName;
    private String lastName;
    private String phoneNumber;
    private Boolean active;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<String> roles;
    private Boolean hasPassword;
    /** Identity provider that owns this account: LOCAL, GOOGLE, or APPLE. */
    private String provider;
    /** True if the user has clicked the email verification link. */
    private Boolean emailVerified;
    /** True if the user has TOTP-based two-factor authentication enabled. */
    private Boolean twoFactorEnabled;
    /** True while the account is in the reversible deletion grace window. */
    private Boolean pendingDeletion;
    /** When the account will be permanently purged if deletion isn't cancelled. */
    private LocalDateTime deletionScheduledAt;
}
