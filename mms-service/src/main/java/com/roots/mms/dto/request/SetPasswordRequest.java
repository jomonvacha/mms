package com.roots.mms.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Used by the disconnect-provider endpoint. When the user has no local password,
 * newPassword is required (and validated). When they already have one, newPassword
 * can be null/empty — the service layer skips it.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SetPasswordRequest {
    // Validation is deferred to UserService.disconnectProvider because the rules
    // depend on whether the user already has a password (server state).
    private String newPassword;
}
