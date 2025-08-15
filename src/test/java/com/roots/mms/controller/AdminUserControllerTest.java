package com.roots.mms.controller;

import com.roots.mms.AbstractIntegrationTest;
import com.roots.mms.dto.request.LoginRequest;
import com.roots.mms.dto.request.UpdateUserRolesRequest;
import com.roots.mms.dto.request.UpdateUserStatusRequest;
import com.roots.mms.entity.ERole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminUserControllerTest extends AbstractIntegrationTest {

  private String adminToken;
  private Long targetUserId;

  @BeforeEach
  void setup() throws Exception {
    createUser("admin", "admin@example.com", "adminpass", List.of(ERole.ROLE_ADMIN, ERole.ROLE_USER));
    adminToken = loginAndGetToken("admin", "adminpass");
    targetUserId = createUser("u2", "u2@example.com", "p2", List.of(ERole.ROLE_USER)).getId();
  }

  @Test
  void testListUsersAndGetOne() throws Exception {
    mockMvc.perform(get("/api/admin/users")
        .header("Authorization", "Bearer " + adminToken))
      .andExpect(status().isOk());

    MvcResult res = mockMvc.perform(get("/api/admin/users/" + targetUserId)
        .header("Authorization", "Bearer " + adminToken))
      .andExpect(status().isOk())
      .andReturn();
    assertThat(res.getResponse().getContentAsString()).contains("u2");
  }

  @Test
  void testUpdateRolesAndStatus() throws Exception {
    UpdateUserRolesRequest rolesReq = new UpdateUserRolesRequest();
    rolesReq.setRoles(List.of("ROLE_USER", "ROLE_MEMBER"));
    MvcResult res = mockMvc.perform(put("/api/admin/users/" + targetUserId + "/roles")
        .header("Authorization", "Bearer " + adminToken)
        .contentType(MediaType.APPLICATION_JSON)
        .content(objectMapper.writeValueAsString(rolesReq)))
      .andExpect(status().isOk())
      .andReturn();
    assertThat(res.getResponse().getContentAsString()).contains("ROLE_MEMBER");

    UpdateUserStatusRequest statusReq = new UpdateUserStatusRequest();
    statusReq.setActive(false);
    mockMvc.perform(put("/api/admin/users/" + targetUserId + "/status")
        .header("Authorization", "Bearer " + adminToken)
        .contentType(MediaType.APPLICATION_JSON)
        .content(objectMapper.writeValueAsString(statusReq)))
      .andExpect(status().isOk());

    // user login should now fail (disabled)
    LoginRequest loginRequest = new LoginRequest();
    loginRequest.setUsername("u2");
    loginRequest.setPassword("p2");
    mockMvc.perform(post("/api/auth/signin")
        .contentType(MediaType.APPLICATION_JSON)
        .content(objectMapper.writeValueAsString(loginRequest)))
      .andExpect(status().isUnauthorized());
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

