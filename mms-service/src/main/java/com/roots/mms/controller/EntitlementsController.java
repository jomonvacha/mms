package com.roots.mms.controller;

import com.roots.mms.dto.response.ResolvedEntitlementsResponse;
import com.roots.mms.exception.AuthorizationException;
import com.roots.mms.exception.ResourceNotFoundException;
import com.roots.mms.repository.UserRepository;
import com.roots.mms.security.SecurityUtils;
import com.roots.mms.service.EntitlementService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Member-facing entitlement read API. The "mine" endpoint is the single source
 * IDFY and other consumers should use when gating features for the current
 * session — it resolves the caller's membership and flattens the result into
 * a simple map of {@code key → value}.
 */
@RestController
@Slf4j
@RequiredArgsConstructor
@RequestMapping("/api/entitlements")
public class EntitlementsController {

    private final EntitlementService entitlementService;
    private final UserRepository userRepository;

    @GetMapping("/mine")
    @PreAuthorize("isAuthenticated()")
    public ResolvedEntitlementsResponse mine() {
        String userId = SecurityUtils.getCurrentUserIdOrNull();
        if (userId == null) {
            // Fall back to username/email for tokens minted with the subject-only shape.
            String principalName = SecurityUtils.getCurrentUsernameOrNull();
            if (principalName == null) {
                throw new AuthorizationException("Entitlements", "read");
            }
            userId = userRepository.findByUsernameOrEmail(principalName, principalName)
                    .orElseThrow(() -> new ResourceNotFoundException("User", "principal", principalName))
                    .getId().toString();
        }
        return entitlementService.resolveForMember(userId);
    }

    @GetMapping("/user/{userId}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('MANAGER') or hasRole('MODERATOR')")
    public ResolvedEntitlementsResponse forUser(@PathVariable String userId) {
        return entitlementService.resolveForMember(userId);
    }
}
