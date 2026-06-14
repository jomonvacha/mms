package com.roots.mms.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Standalone-setup MockMvc test for {@link InfoController}. Uses
 * {@link MockMvcBuilders#standaloneSetup} so no Spring context is loaded —
 * just the controller, the default message converters, and the route mappings.
 * That keeps the test pure and Docker-free, and avoids Spring Boot 4's
 * web-test-autoconfigure churn.
 */
class InfoControllerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new InfoController()).build();
    }

    @Test
    void getApiInfo_returnsServiceMetadataAndFeatures() throws Exception {
        mockMvc.perform(get("/api/info"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Member Management System API"))
                .andExpect(jsonPath("$.version").value("1.0.0"))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.java_version").exists())
                .andExpect(jsonPath("$.features.authentication").value("JWT with refresh tokens"))
                .andExpect(jsonPath("$.features.database").value("PostgreSQL"));
    }

    @Test
    void getHealth_returnsUp() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.service").value("Member Management System"))
                .andExpect(jsonPath("$.timestamp").exists());
    }
}
