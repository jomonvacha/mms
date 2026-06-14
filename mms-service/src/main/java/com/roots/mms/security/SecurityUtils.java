package com.roots.mms.security;

import com.roots.mms.security.services.UserPrincipal;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

public final class SecurityUtils {
    private SecurityUtils() {}

    public static Optional<UserPrincipal> getCurrentUserPrincipal() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UserPrincipal) {
            return Optional.of((UserPrincipal) auth.getPrincipal());
        }
        return Optional.empty();
    }

    public static String getCurrentUserIdOrNull() {
        return getCurrentUserPrincipal().map(UserPrincipal::getId).orElse(null);
    }

    public static String getCurrentUserIdOrThrow() {
        String id = getCurrentUserIdOrNull();
        if (id == null) {
            throw new com.roots.mms.exception.AuthenticationException("Not authenticated");
        }
        return id;
    }

    /**
     * Returns the raw principal name (username or email) for any authenticated
     * caller — including OAuth2 and stateless JWT contexts where the
     * principal is not a {@link UserPrincipal}. Returns null when anonymous.
     */
    public static String getCurrentUsernameOrNull() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) return null;
        String name = auth.getName();
        if (name == null || "anonymousUser".equals(name)) return null;
        return name;
    }
}
