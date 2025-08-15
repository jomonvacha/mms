package com.roots.mms.controller;

import com.roots.mms.AbstractIntegrationTest;
import com.roots.mms.dto.request.ChangePasswordRequest;
import com.roots.mms.dto.request.LoginRequest;
import com.roots.mms.dto.request.UpdateUserProfileRequest;
import com.roots.mms.entity.ERole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class UserControllerTest extends AbstractIntegrationTest {

  private String token;

  @BeforeEach
  void setupUser() throws Exception {
    createUser("user1", "user1@example.com", "pass1234", List.of(ERole.ROLE_USER));
    token = loginAndGetToken("user1", "pass1234");
  }

  @Test
  void testGetMe() throws Exception {
    MvcResult res = mockMvc.perform(get("/api/users/me")
        .header("Authorization", "Bearer " + token))
      .andExpect(status().isOk())
      .andReturn();
    String json = res.getResponse().getContentAsString();
    assertThat(json).contains("user1");
  }

  @Test
  void testUpdateMe() throws Exception {
    UpdateUserProfileRequest req = new UpdateUserProfileRequest();
    req.setFirstName("New");
    req.setLastName("Name");
    req.setPhoneNumber("1112223333");
    MvcResult res = mockMvc.perform(put("/api/users/me")
        .header("Authorization", "Bearer " + token)
        .contentType(MediaType.APPLICATION_JSON)
        .content(objectMapper.writeValueAsString(req)))
      .andExpect(status().isOk())
      .andReturn();
    String json = res.getResponse().getContentAsString();
    assertThat(json).contains("New").contains("Name").contains("1112223333");
  }

  @Test
  void testChangePassword() throws Exception {
    ChangePasswordRequest req = new ChangePasswordRequest();
    req.setCurrentPassword("pass1234");
    req.setNewPassword("newPass987");
    mockMvc.perform(put("/api/users/me/password")
        .header("Authorization", "Bearer " + token)
        .contentType(MediaType.APPLICATION_JSON)
        .content(objectMapper.writeValueAsString(req)))
      .andExpect(status().isOk());

    // login with new password works
    String newToken = loginAndGetToken("user1", "newPass987");
    assertThat(newToken).isNotBlank();
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
    String json = res.getResponse().getContentAsString();
    return objectMapper.readTree(json).get("accessToken").asText();
  }
}

