package com.roots.mms.controller;

import com.roots.mms.AbstractIntegrationTest;
import com.roots.mms.dto.request.AccountDeletionRequest;
import com.roots.mms.dto.request.LoginRequest;
import com.roots.mms.entity.ERole;
import com.roots.mms.entity.User;
import com.roots.mms.scheduled.AccountDeletionJob;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AccountDeletionControllerTest extends AbstractIntegrationTest {

    @Autowired
    private AccountDeletionJob accountDeletionJob;

    private String token;

    @BeforeEach
    void setupUser() throws Exception {
        createUser("del_user", "del@example.com", "pass1234", List.of(ERole.ROLE_MEMBER));
        token = login("del_user", "pass1234");
    }

    @Test
    void requestThenCancel_togglesPendingDeletion() throws Exception {
        mockMvc.perform(post("/api/users/me/deletion")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AccountDeletionRequest("pass1234"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pendingDeletion").value(true))
                .andExpect(jsonPath("$.deletionScheduledAt").isNotEmpty());

        mockMvc.perform(get("/api/users/me").header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.pendingDeletion").value(true));

        mockMvc.perform(delete("/api/users/me/deletion").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pendingDeletion").value(false));
    }

    @Test
    void request_wrongPassword_rejected() throws Exception {
        mockMvc.perform(post("/api/users/me/deletion")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AccountDeletionRequest("nope"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void purgeJob_deletesAccountsPastGraceWindow() {
        User u = userRepository.findByUsername("del_user").orElseThrow();
        u.setPendingDeletion(true);
        u.setDeletionScheduledAt(LocalDateTime.now().minusDays(1)); // window already elapsed
        userRepository.save(u);

        accountDeletionJob.purgeExpiredDeletions();

        assertThat(userRepository.findByUsername("del_user")).isEmpty();
    }

    @Test
    void purgeJob_leavesAccountsWithinGraceWindow() {
        User u = userRepository.findByUsername("del_user").orElseThrow();
        u.setPendingDeletion(true);
        u.setDeletionScheduledAt(LocalDateTime.now().plusDays(10)); // still within window
        userRepository.save(u);

        accountDeletionJob.purgeExpiredDeletions();

        assertThat(userRepository.findByUsername("del_user")).isPresent();
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
