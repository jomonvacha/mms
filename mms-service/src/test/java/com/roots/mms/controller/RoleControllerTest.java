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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RoleControllerTest extends AbstractIntegrationTest {

  private String token;

  @BeforeEach
  void setup() throws Exception {
    createUser("manager", "mgr@example.com", "pass", List.of(ERole.ROLE_ADMIN, ERole.ROLE_MEMBER));
    token = loginAndGetToken("manager", "pass");
  }

  @Test
  void listRoles() throws Exception {
    MvcResult res = mockMvc.perform(get("/api/roles").header("Authorization", "Bearer " + token))
      .andExpect(status().isOk())
      .andReturn();
    String json = res.getResponse().getContentAsString();
    assertThat(json).contains("ROLE_ADMIN").contains("ROLE_MEMBER").contains("ROLE_MANAGER");
  }

  private String loginAndGetToken(String username, String password) throws Exception {
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

