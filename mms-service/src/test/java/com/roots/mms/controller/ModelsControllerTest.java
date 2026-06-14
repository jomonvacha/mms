package com.roots.mms.controller;

import com.roots.mms.security.services.UserPrincipal;
import com.roots.mms.service.AiModelService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ModelsControllerTest {

    private AiModelService service;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service = mock(AiModelService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new ModelsController(service)).build();
    }

    @AfterEach
    void clearAuth() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void mine_unauthenticated_returns401() throws Exception {
        // No SecurityContext → SecurityUtils.getCurrentUserIdOrNull() returns
        // null → controller short-circuits with 401 rather than calling the
        // service. Documents the defensive check.
        mockMvc.perform(get("/api/models/mine"))
                .andExpect(status().isUnauthorized());

        verify(service, never()).resolveForMember(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void mine_authenticatedNonUserPrincipal_returns401() throws Exception {
        // A plain string principal (e.g. service-token) doesn't satisfy the
        // UserPrincipal cast — getCurrentUserIdOrNull returns null, so 401.
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("svc-account", "n/a", List.of()));

        mockMvc.perform(get("/api/models/mine"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void mine_authenticated_resolvesForCurrentUser() throws Exception {
        UUID userId = UUID.randomUUID();
        UserPrincipal principal = mock(UserPrincipal.class);
        when(principal.getId()).thenReturn(userId.toString());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, "n/a", List.of()));

        AiModelService.ResolvedCatalog catalog = new AiModelService.ResolvedCatalog(
                "PERSONAL", "FREE", "gpt-4o-mini", List.of());
        when(service.resolveForMember(userId.toString())).thenReturn(catalog);

        mockMvc.perform(get("/api/models/mine"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categoryCode").value("PERSONAL"))
                .andExpect(jsonPath("$.tierCode").value("FREE"))
                .andExpect(jsonPath("$.defaultModelCode").value("gpt-4o-mini"));

        verify(service).resolveForMember(userId.toString());
    }
}
