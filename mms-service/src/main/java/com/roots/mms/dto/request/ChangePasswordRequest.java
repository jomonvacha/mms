package com.roots.mms.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChangePasswordRequest {
  @NotBlank
  private String currentPassword;

  // Same complexity rule as SignupRequest: ≥8 chars with at least one letter
  // and one digit. UserService.changePassword applies the same regex defensively.
  @NotBlank
  @Size(min = 8, max = 64)
  @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$",
          message = "Password must contain at least one letter and one number")
  private String newPassword;
}
