package com.roots.mms.security;

import com.roots.mms.security.services.UserPrincipal;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

public final class SecurityUtils {
  private SecurityUtils() {
  }

  public static Optional<UserPrincipal> getCurrentUserPrincipal() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth != null && auth.getPrincipal() instanceof UserPrincipal) {
      return Optional.of((UserPrincipal) auth.getPrincipal());
    }
    return Optional.empty();
  }

  public static Long getCurrentUserIdOrNull() {
    return getCurrentUserPrincipal().map(UserPrincipal::getId).orElse(null);
  }
}

