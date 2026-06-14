package com.roots.mms.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.roots.mms.entity.SignupInviteCode;
import com.roots.mms.exception.GlobalExceptionHandler;
import com.roots.mms.service.SignupInviteCodeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminSignupInviteControllerTest {

    private SignupInviteCodeService service;
    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        service = mock(SignupInviteCodeService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new AdminSignupInviteController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void list_returnsAllCodes() throws Exception {
        SignupInviteCode c = SignupInviteCode.builder().code("ABC").active(true).build();
        when(service.listAll()).thenReturn(List.of(c));

        mockMvc.perform(get("/api/admin/signup-invites"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].code").value("ABC"));
    }

    @Test
    void create_parsesAllFieldsIncludingExpiresAt() throws Exception {
        Instant expires = Instant.parse("2026-12-31T23:59:00Z");
        SignupInviteCode created = SignupInviteCode.builder()
                .code("ABC").description("desc").maxUses(5).expiresAt(expires).active(true).build();
        when(service.create(eq("ABC"), eq("desc"), eq(5), eq(expires), any())).thenReturn(created);

        Map<String, Object> body = new HashMap<>();
        body.put("code", "ABC");
        body.put("description", "desc");
        body.put("maxUses", 5);
        body.put("expiresAt", "2026-12-31T23:59:00Z");

        mockMvc.perform(post("/api/admin/signup-invites")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("ABC"))
                .andExpect(jsonPath("$.maxUses").value(5));

        verify(service).create(eq("ABC"), eq("desc"), eq(5), eq(expires), any());
    }

    @Test
    void create_treatsBlankExpiresAtAsNull() throws Exception {
        // Blank string in expiresAt must be normalized to null — not parsed,
        // not 1970-epoch-zero. Otherwise frontend default empty fields would
        // create immediately-expired invite codes.
        SignupInviteCode created = SignupInviteCode.builder().code("X").build();
        when(service.create(eq("X"), any(), any(), isNull(), any())).thenReturn(created);

        Map<String, Object> body = new HashMap<>();
        body.put("code", "X");
        body.put("expiresAt", "  ");

        mockMvc.perform(post("/api/admin/signup-invites")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk());

        verify(service).create(eq("X"), any(), any(), isNull(), any());
    }

    @Test
    void create_omittedMaxUses_passesNull() throws Exception {
        SignupInviteCode created = SignupInviteCode.builder().code("X").build();
        when(service.create(anyString(), any(), isNull(), any(), any())).thenReturn(created);

        Map<String, Object> body = new HashMap<>();
        body.put("code", "X");

        mockMvc.perform(post("/api/admin/signup-invites")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk());

        verify(service).create(eq("X"), any(), isNull(), any(), any());
    }

    @Test
    void update_parsesPatchAndDelegates() throws Exception {
        String id = UUID.randomUUID().toString();
        Instant expires = Instant.parse("2027-01-01T00:00:00Z");
        SignupInviteCode updated = SignupInviteCode.builder().code("X").active(false).build();
        when(service.update(eq(id), eq(false), eq(10), eq(expires), eq("note"))).thenReturn(updated);

        Map<String, Object> body = new HashMap<>();
        body.put("active", false);
        body.put("maxUses", 10);
        body.put("expiresAt", "2027-01-01T00:00:00Z");
        body.put("description", "note");

        mockMvc.perform(put("/api/admin/signup-invites/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));

        verify(service).update(id, false, 10, expires, "note");
    }

    @Test
    void delete_returnsNoContent() throws Exception {
        String id = UUID.randomUUID().toString();

        mockMvc.perform(delete("/api/admin/signup-invites/" + id))
                .andExpect(status().isNoContent());

        verify(service).delete(id);
    }
}
