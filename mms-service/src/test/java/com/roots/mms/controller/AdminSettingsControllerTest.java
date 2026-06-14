package com.roots.mms.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.roots.mms.entity.AppSetting;
import com.roots.mms.exception.GlobalExceptionHandler;
import com.roots.mms.service.AppSettingService;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminSettingsControllerTest {

    private AppSettingService service;
    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        service = mock(AppSettingService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new AdminSettingsController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @AfterEach
    void clearAuth() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void list_returnsAllSettings() throws Exception {
        AppSetting a = AppSetting.builder().key("k1").value("v1").build();
        when(service.listAll()).thenReturn(List.of(a));

        mockMvc.perform(get("/api/admin/settings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].key").value("k1"))
                .andExpect(jsonPath("$[0].value").value("v1"));
    }

    @Test
    void update_missingValue_returnsBadRequest() throws Exception {
        // BusinessRuleException maps to 400 via the global advice; the handler
        // is what makes this end-to-end behavior visible.
        mockMvc.perform(put("/api/admin/settings/some.key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("notValue", "x"))))
                .andExpect(status().isBadRequest());

        verify(service, never()).set(any(), any(), any());
    }

    @Test
    void update_passesUpdaterFromSecurityContext() throws Exception {
        // Authenticated principal: getCurrentUserIdOrNull() returns null (it
        // requires a UserPrincipal), so the controller passes null. Verifies
        // the controller does not invent an updater string.
        AppSetting saved = AppSetting.builder().key("flag").value("true").build();
        when(service.set(eq("flag"), eq("true"), any())).thenReturn(saved);

        mockMvc.perform(put("/api/admin/settings/flag")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("value", "true"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.key").value("flag"))
                .andExpect(jsonPath("$.value").value("true"));

        verify(service).set("flag", "true", null);
    }

    @Test
    void update_authenticatedNonUserPrincipal_passesNull() throws Exception {
        // SecurityUtils.getCurrentUserIdOrNull only resolves for UserPrincipal;
        // a plain string principal (e.g. JWT subject) yields null. We document
        // that null is passed to the service and let the service decide.
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("admin@example.com", "n/a", List.of()));
        AppSetting saved = AppSetting.builder().key("flag").value("v").build();
        when(service.set(eq("flag"), eq("v"), any())).thenReturn(saved);

        mockMvc.perform(put("/api/admin/settings/flag")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("value", "v"))))
                .andExpect(status().isOk());

        verify(service).set("flag", "v", null);
    }
}
