package com.roots.mms.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Request to start a verified email change. */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChangeEmailRequest {

    @NotBlank
    @Email
    @Size(max = 254)
    private String newEmail;

    /** Re-authenticate the sensitive action with the current password. */
    @NotBlank
    private String currentPassword;
}
