package com.roots.mms.controller;

import com.roots.mms.AbstractIntegrationTest;
import com.roots.mms.dto.request.CreateMemberRequest;
import com.roots.mms.dto.request.LoginRequest;
import com.roots.mms.entity.ERole;
import com.roots.mms.entity.MembershipType;
import com.roots.mms.repository.MemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MemberControllerTest extends AbstractIntegrationTest {

  @Autowired
  private MemberRepository memberRepository;

  private String modToken;
  private String userToken;
  private String userId;

  @BeforeEach
  void setup() throws Exception {
    createUser("mod", "mod@example.com", "pass", List.of(ERole.ROLE_MODERATOR, ERole.ROLE_MEMBER));
    modToken = loginAndGetToken("mod", "pass");

    userId = createUser("u1", "u1@example.com", "p1", List.of(ERole.ROLE_MEMBER)).getId().toString();
    userToken = loginAndGetToken("u1", "p1");
  }

  @Test
  void createMemberAsModerator_andAccessRules() throws Exception {
    CreateMemberRequest req = new CreateMemberRequest();
    req.setUserId(userId);
    req.setMembershipType(MembershipType.BASIC);
    req.setMembershipStartDate(LocalDate.now());

    MvcResult created = mockMvc.perform(post("/api/members")
        .header("Authorization", "Bearer " + modToken)
        .contentType(MediaType.APPLICATION_JSON)
        .content(objectMapper.writeValueAsString(req)))
      .andExpect(status().isOk())
      .andReturn();
    String memberId = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asText();
    assertThat(memberId).isNotBlank();

    // owner can access own member via /user/{id}
    mockMvc.perform(get("/api/members/user/" + userId)
        .header("Authorization", "Bearer " + userToken))
      .andExpect(status().isOk());

    // non-owner user denied
    createUser("u2", "u2@example.com", "p2", List.of(ERole.ROLE_MEMBER));
    String otherToken = loginAndGetToken("u2", "p2");
    mockMvc.perform(get("/api/members/" + memberId)
        .header("Authorization", "Bearer " + otherToken))
      .andExpect(status().isForbidden());

    // moderator can list
    mockMvc.perform(get("/api/members?page=0&size=10")
        .header("Authorization", "Bearer " + modToken))
      .andExpect(status().isOk());
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

