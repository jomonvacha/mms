package com.roots.mms.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.roots.mms.exception.AuthenticationException;
import com.roots.mms.exception.GlobalExceptionHandler;
import com.roots.mms.security.services.UserPrincipal;
import com.roots.mms.service.TwoFactorService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TwoFactorControllerTest {

    private TwoFactorService service;
    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        service = mock(TwoFactorService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new TwoFactorController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @AfterEach
    void clearAuth() {
        SecurityContextHolder.clearContext();
    }

    private UUID authenticateUser() {
        UUID id = UUID.randomUUID();
        UserPrincipal principal = mock(UserPrincipal.class);
        when(principal.getId()).thenReturn(id.toString());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, "n/a", List.of()));
        return id;
    }

    @Test
    void setup_returnsSetupResult() throws Exception {
        UUID id = authenticateUser();
        TwoFactorService.SetupResult result =
                new TwoFactorService.SetupResult("SECRET", "otpauth://totp/Roots:alice", "");
        when(service.startSetup(id.toString())).thenReturn(result);

        mockMvc.perform(post("/api/users/me/2fa/setup"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.secret").value("SECRET"))
                .andExpect(jsonPath("$.otpauthUri").value("otpauth://totp/Roots:alice"));
    }

    @Test
    void setup_unauthenticated_throwsAuthenticationException() throws Exception {
        // getCurrentUserIdOrThrow throws when there's no UserPrincipal — the
        // global advice maps that to 401. This is the contract that frontends
        // rely on to redirect to the login flow.
        mockMvc.perform(post("/api/users/me/2fa/setup"))
                .andExpect(status().isUnauthorized());

        verify(service, never()).startSetup(anyString());
    }

    @Test
    void enable_validBody_returnsRecoveryCodes() throws Exception {
        UUID id = authenticateUser();
        TwoFactorService.EnableResult result = new TwoFactorService.EnableResult(
                List.of("AAAAA-BBBBB", "CCCCC-DDDDD"));
        when(service.confirmSetup(id.toString(), "123456")).thenReturn(result);

        mockMvc.perform(post("/api/users/me/2fa/enable")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("code", "123456"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recoveryCodes[0]").value("AAAAA-BBBBB"))
                .andExpect(jsonPath("$.recoveryCodes.length()").value(2));
    }

    @Test
    void enable_blankCode_failsValidation() throws Exception {
        authenticateUser();

        mockMvc.perform(post("/api/users/me/2fa/enable")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("code", "  "))))
                .andExpect(status().isBadRequest());

        verify(service, never()).confirmSetup(any(), any());
    }

    @Test
    void disable_validBody_returnsNoContent() throws Exception {
        UUID id = authenticateUser();

        mockMvc.perform(post("/api/users/me/2fa/disable")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("currentPasswordOrCode", "pw1234"))))
                .andExpect(status().isNoContent());

        verify(service).disable(id.toString(), "pw1234");
    }

    @Test
    void disable_blankCode_failsValidation() throws Exception {
        authenticateUser();

        mockMvc.perform(post("/api/users/me/2fa/disable")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("currentPasswordOrCode", ""))))
                .andExpect(status().isBadRequest());

        verify(service, never()).disable(any(), any());
    }
}
