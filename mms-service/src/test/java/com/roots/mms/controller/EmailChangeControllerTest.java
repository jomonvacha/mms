package com.roots.mms.controller;

import com.roots.mms.AbstractIntegrationTest;
import com.roots.mms.dto.request.ChangeEmailRequest;
import com.roots.mms.dto.request.LoginRequest;
import com.roots.mms.dto.request.UpdateUserProfileRequest;
import com.roots.mms.entity.ERole;
import com.roots.mms.entity.VerificationToken;
import com.roots.mms.repository.VerificationTokenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class EmailChangeControllerTest extends AbstractIntegrationTest {

    @Autowired
    private VerificationTokenRepository tokenRepository;

    private String token;

    @BeforeEach
    void setupUser() throws Exception {
        createUser("ec_user", "old@example.com", "pass1234", List.of(ERole.ROLE_MEMBER));
        token = login("ec_user", "pass1234");
    }

    @Test
    void profilePut_cannotChangeEmail() throws Exception {
        UpdateUserProfileRequest req = new UpdateUserProfileRequest();
        req.setEmail("sneaky@example.com");
        mockMvc.perform(put("/api/users/me")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
        assertThat(userRepository.findByUsername("ec_user").orElseThrow().getEmail())
                .isEqualTo("old@example.com");
    }

    @Test
    void requestThenConfirm_changesEmail() throws Exception {
        ChangeEmailRequest req = new ChangeEmailRequest("new@example.com", "pass1234");
        mockMvc.perform(post("/api/users/me/email-change/request")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());

        VerificationToken t = tokenRepository.findAll().stream()
                .filter(x -> x.getType() == VerificationToken.TokenType.EMAIL_CHANGE)
                .findFirst().orElseThrow();
        assertThat(t.getNewEmail()).isEqualTo("new@example.com");

        // Email not changed until confirmation.
        assertThat(userRepository.findByUsername("ec_user").orElseThrow().getEmail())
                .isEqualTo("old@example.com");

        mockMvc.perform(get("/api/auth/confirm-email-change").param("token", t.getToken()))
                .andExpect(status().isOk());

        var updated = userRepository.findByUsername("ec_user").orElseThrow();
        assertThat(updated.getEmail()).isEqualTo("new@example.com");
        assertThat(updated.getEmailVerified()).isTrue();
    }

    @Test
    void request_wrongPassword_rejected() throws Exception {
        ChangeEmailRequest req = new ChangeEmailRequest("new@example.com", "wrongpass");
        mockMvc.perform(post("/api/users/me/email-change/request")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    private String login(String username, String password) throws Exception {
        LoginRequest login = new LoginRequest();
        login.setUsername(username);
        login.setPassword(password);
        MvcResult res = mockMvc.perform(post("/api/auth/signin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(login)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(res.getResponse().getContentAsString()).get("accessToken").asText();
    }
}
