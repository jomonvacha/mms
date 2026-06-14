package com.roots.mms.controller;

import com.roots.mms.service.email.LoggingEmailService;
import com.roots.mms.service.email.OutgoingEmail;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DevMailControllerTest {

    private LoggingEmailService loggingEmailService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        loggingEmailService = mock(LoggingEmailService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new DevMailController(loggingEmailService)).build();
    }

    @Test
    void recent_returnsCapturedEmails() throws Exception {
        when(loggingEmailService.recent()).thenReturn(List.of(
                OutgoingEmail.of("a@example.com", "Hi", "body")));

        mockMvc.perform(get("/api/dev/mail"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].to").value("a@example.com"))
                .andExpect(jsonPath("$[0].subject").value("Hi"));
    }

    @Test
    void lastTo_known_returnsEmail() throws Exception {
        when(loggingEmailService.lastTo("a@example.com")).thenReturn(
                OutgoingEmail.of("a@example.com", "Reset", "link"));

        mockMvc.perform(get("/api/dev/mail/last").param("to", "a@example.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subject").value("Reset"));
    }

    @Test
    void lastTo_unknown_returns404() throws Exception {
        // No matching email in the in-memory store. Controller maps null to
        // 404 directly (without going through the global advice) so the
        // dev/test workflow gets the right signal.
        when(loggingEmailService.lastTo("nobody@example.com")).thenReturn(null);

        mockMvc.perform(get("/api/dev/mail/last").param("to", "nobody@example.com"))
                .andExpect(status().isNotFound());
    }

    @Test
    void lastTo_missingParam_returns400() throws Exception {
        mockMvc.perform(get("/api/dev/mail/last"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void clear_returnsNoContent() throws Exception {
        mockMvc.perform(delete("/api/dev/mail"))
                .andExpect(status().isNoContent());

        verify(loggingEmailService).clear();
    }
}
