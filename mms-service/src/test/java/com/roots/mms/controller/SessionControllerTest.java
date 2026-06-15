package com.roots.mms.controller;

import com.roots.mms.AbstractIntegrationTest;
import com.roots.mms.dto.request.LoginRequest;
import com.roots.mms.entity.ERole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SessionControllerTest extends AbstractIntegrationTest {

    @BeforeEach
    void setupUser() {
        createUser("sessuser", "sess@example.com", "pass1234", List.of(ERole.ROLE_MEMBER));
    }

    @Test
    void signinCreatesSession_andListShowsCurrent() throws Exception {
        String token = login("Mozilla/5.0 (Macintosh) Chrome/120");
        mockMvc.perform(get("/api/users/me/sessions").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].current").value(true))
                .andExpect(jsonPath("$[0].deviceLabel").value("Chrome on macOS"));
    }

    @Test
    void revokeOtherSession_invalidatesItsTokenImmediately() throws Exception {
        String tokenA = login("Mozilla/5.0 (Macintosh) Chrome/120");      // session A
        String tokenB = login("Mozilla/5.0 (Windows NT 10) Firefox/121"); // session B

        // B lists two sessions; find A's id (the non-current one).
        MvcResult res = mockMvc.perform(get("/api/users/me/sessions")
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andReturn();
        var arr = objectMapper.readTree(res.getResponse().getContentAsString());
        String otherId = null;
        for (var node : arr) {
            if (!node.get("current").asBoolean()) otherId = node.get("id").asText();
        }
        assertThat(otherId).isNotNull();

        // B revokes A.
        mockMvc.perform(delete("/api/users/me/sessions/" + otherId)
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isOk());

        // A's access token is now rejected (remote revoke took effect immediately).
        mockMvc.perform(get("/api/users/me").header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isUnauthorized());
        // B still works.
        mockMvc.perform(get("/api/users/me").header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isOk());
    }

    @Test
    void signOutEverywhereElse_keepsCurrentOnly() throws Exception {
        login("Mozilla/5.0 (Macintosh) Chrome/120");
        login("Mozilla/5.0 (Windows NT 10) Firefox/121");
        String tokenC = login("Mozilla/5.0 (X11; Linux) Chrome/119");

        mockMvc.perform(delete("/api/users/me/sessions").header("Authorization", "Bearer " + tokenC))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/users/me/sessions").header("Authorization", "Bearer " + tokenC))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].current").value(true));
    }

    private String login(String userAgent) throws Exception {
        LoginRequest login = new LoginRequest();
        login.setUsername("sessuser");
        login.setPassword("pass1234");
        MvcResult res = mockMvc.perform(post("/api/auth/signin")
                        .header("User-Agent", userAgent)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(login)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(res.getResponse().getContentAsString()).get("accessToken").asText();
    }
}
