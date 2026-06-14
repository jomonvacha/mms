package com.roots.mms.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.roots.mms.entity.EnforcementRule;
import com.roots.mms.exception.GlobalExceptionHandler;
import com.roots.mms.exception.ResourceNotFoundException;
import com.roots.mms.service.EnforcementRuleService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class EnforcementRuleControllerTest {

    private EnforcementRuleService service;
    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        service = mock(EnforcementRuleService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new EnforcementRuleController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void getSystemRules_returnsList() throws Exception {
        EnforcementRule r = EnforcementRule.builder().text("rule").tier("SYSTEM").build();
        when(service.getSystemRules()).thenReturn(List.of(r));

        mockMvc.perform(get("/api/enforcement-rules/system"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].tier").value("SYSTEM"));
    }

    @Test
    void getOptionalRules_returnsList() throws Exception {
        when(service.getOptionalRules()).thenReturn(List.of());

        mockMvc.perform(get("/api/enforcement-rules/optional"))
                .andExpect(status().isOk());

        verify(service).getOptionalRules();
    }

    @Test
    void getAll_adminEndpoint_returnsList() throws Exception {
        when(service.getAll()).thenReturn(List.of());

        mockMvc.perform(get("/api/admin/enforcement-rules"))
                .andExpect(status().isOk());

        verify(service).getAll();
    }

    @Test
    void create_passesRuleToService() throws Exception {
        EnforcementRule rule = EnforcementRule.builder()
                .text("new rule").tier("OPTIONAL").category("style").build();
        when(service.create(any(EnforcementRule.class))).thenReturn(rule);

        mockMvc.perform(post("/api/admin/enforcement-rules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(rule)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.text").value("new rule"));
    }

    @Test
    void update_passesIdAndPatch() throws Exception {
        String id = UUID.randomUUID().toString();
        EnforcementRule updated = EnforcementRule.builder().text("updated").tier("SYSTEM").build();
        when(service.update(eq(id), anyMap())).thenReturn(updated);

        mockMvc.perform(put("/api/admin/enforcement-rules/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("text", "updated"))))
                .andExpect(status().isOk());

        verify(service).update(eq(id), anyMap());
    }

    @Test
    void update_unknownId_mapsTo404ViaGlobalAdvice() throws Exception {
        // Confirms the @RestControllerAdvice is registered correctly — the
        // service throws ResourceNotFoundException, the advice maps it to 404
        // with the standard ErrorResponse shape.
        when(service.update(anyString(), anyMap()))
                .thenThrow(new ResourceNotFoundException("EnforcementRule", "id", "ghost"));

        mockMvc.perform(put("/api/admin/enforcement-rules/ghost")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"));
    }

    @Test
    void delete_returnsNoContent() throws Exception {
        String id = UUID.randomUUID().toString();

        mockMvc.perform(delete("/api/admin/enforcement-rules/" + id))
                .andExpect(status().isNoContent());

        verify(service).delete(id);
    }
}
