package com.roots.mms.controller;

import com.roots.mms.dto.response.ResolvedEntitlementsResponse;
import com.roots.mms.entity.User;
import com.roots.mms.exception.GlobalExceptionHandler;
import com.roots.mms.repository.UserRepository;
import com.roots.mms.security.services.UserPrincipal;
import com.roots.mms.service.EntitlementService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class EntitlementsControllerTest {

    private EntitlementService entitlementService;
    private UserRepository userRepository;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        entitlementService = mock(EntitlementService.class);
        userRepository = mock(UserRepository.class);
        mockMvc = MockMvcBuilders.standaloneSetup(
                new EntitlementsController(entitlementService, userRepository))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @AfterEach
    void clearAuth() {
        SecurityContextHolder.clearContext();
    }

    private ResolvedEntitlementsResponse stubResolved(String userId) {
        return ResolvedEntitlementsResponse.builder()
                .userId(userId).categoryCode("PERSONAL").tierCode("FREE")
                .entitlements(Map.of("a", "true")).build();
    }

    @Test
    void mine_userPrincipal_resolvesByUserId() throws Exception {
        UUID id = UUID.randomUUID();
        UserPrincipal principal = mock(UserPrincipal.class);
        when(principal.getId()).thenReturn(id.toString());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, "n/a", List.of()));

        when(entitlementService.resolveForMember(id.toString())).thenReturn(stubResolved(id.toString()));

        mockMvc.perform(get("/api/entitlements/mine"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(id.toString()));

        // Bypassing the user-by-name fallback when a UserPrincipal is present.
        verify(userRepository, never()).findByUsernameOrEmail(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void mine_jwtSubjectPrincipal_fallsBackToUserLookup() throws Exception {
        // No UserPrincipal — falls back to username/email lookup so that
        // tokens minted with just a `sub` claim still resolve.
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("alice@example.com", "n/a", List.of()));

        UUID userId = UUID.randomUUID();
        User user = new User("alice", "alice@example.com", "x", "Alice", "Smith");
        user.setId(userId);
        when(userRepository.findByUsernameOrEmail("alice@example.com", "alice@example.com"))
                .thenReturn(Optional.of(user));
        when(entitlementService.resolveForMember(userId.toString()))
                .thenReturn(stubResolved(userId.toString()));

        mockMvc.perform(get("/api/entitlements/mine"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(userId.toString()));
    }

    @Test
    void mine_unknownPrincipalName_returns404FromUserLookup() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("ghost@example.com", "n/a", List.of()));
        when(userRepository.findByUsernameOrEmail("ghost@example.com", "ghost@example.com"))
                .thenReturn(Optional.empty());

        mockMvc.perform(get("/api/entitlements/mine"))
                .andExpect(status().isNotFound());
    }

    @Test
    void mine_unauthenticated_returns403FromAuthorizationException() throws Exception {
        // No principal at all → AuthorizationException → 403 via global advice.
        // Documents that the controller distinguishes "no auth" from "auth but
        // unknown user" — the former is a 403, the latter is a 404.
        mockMvc.perform(get("/api/entitlements/mine"))
                .andExpect(status().isForbidden());
    }

    @Test
    void forUser_resolvesGivenUserId() throws Exception {
        String userId = UUID.randomUUID().toString();
        when(entitlementService.resolveForMember(userId)).thenReturn(stubResolved(userId));

        mockMvc.perform(get("/api/entitlements/user/" + userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(userId));
    }
}
