package com.roots.mms.dto.request;

import jakarta.validation.constraints.Email;
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
public class SignupRequest {
  @NotBlank
  @Size(min = 3, max = 20)
  private String username;

  @NotBlank
  @Size(max = 50)
  @Email
  private String email;

  // Same complexity rule as ChangePasswordRequest: ≥8 chars with at least
  // one letter and one digit. Kept in sync intentionally — see UserService.changePassword.
  @NotBlank
  @Size(min = 8, max = 64)
  @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$",
          message = "Password must contain at least one letter and one number")
  private String password;

  @NotBlank
  @Size(max = 50)
  private String firstName;

  @NotBlank
  @Size(max = 50)
  private String lastName;

  @Size(max = 15)
  private String phoneNumber;

  /**
   * Invite code issued by an admin. Required or optional depending on the
   * {@code signup.inviteRequired} platform setting; the controller enforces.
   */
  @Size(max = 64)
  private String inviteCode;
}
