package com.roots.mms.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LoginRequest {
  @NotBlank
  private String username;

  @NotBlank
  private String password;

  /**
   * Optional TOTP code (or recovery code) — required when the account has 2FA
   * enabled. When missing on a 2FA-enabled account, signin returns HTTP 401 with
   * error code TWO_FACTOR_REQUIRED so the client can prompt for it.
   */
  private String totpCode;
}
