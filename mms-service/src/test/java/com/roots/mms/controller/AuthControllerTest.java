package com.roots.mms.controller;

import com.roots.mms.AbstractIntegrationTest;
import com.roots.mms.dto.request.LoginRequest;
import com.roots.mms.dto.request.SignupRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthControllerTest extends AbstractIntegrationTest {

  // `IDFY-WELCOME` is seeded by DataInitializer with 100 uses. Plenty for the
  // per-container lifetime of these tests; the Testcontainer DB resets per-suite.
  private static final String STARTER_INVITE = "IDFY-WELCOME";

  @Test
  void testSignup() throws Exception {
    SignupRequest signupRequest = new SignupRequest();
    signupRequest.setUsername("testuser");
    signupRequest.setEmail("test@example.com");
    signupRequest.setPassword("password123");
    signupRequest.setFirstName("Test");
    signupRequest.setLastName("User");
    signupRequest.setInviteCode(STARTER_INVITE);

    mockMvc.perform(post("/api/auth/signup")
        .contentType(MediaType.APPLICATION_JSON)
        .content(objectMapper.writeValueAsString(signupRequest)))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.message").value("User registered successfully!"));
  }

  @Test
  void testSignin() throws Exception {
    // First, create a user
    SignupRequest signupRequest = new SignupRequest();
    signupRequest.setUsername("loginuser");
    signupRequest.setEmail("login@example.com");
    signupRequest.setPassword("password123");
    signupRequest.setFirstName("Login");
    signupRequest.setLastName("User");
    signupRequest.setInviteCode(STARTER_INVITE);

    mockMvc.perform(post("/api/auth/signup")
      .contentType(MediaType.APPLICATION_JSON)
      .content(objectMapper.writeValueAsString(signupRequest)))
      .andExpect(status().isOk());

    // Then, try to login
    LoginRequest loginRequest = new LoginRequest();
    loginRequest.setUsername("loginuser");
    loginRequest.setPassword("password123");

    mockMvc.perform(post("/api/auth/signin")
        .contentType(MediaType.APPLICATION_JSON)
        .content(objectMapper.writeValueAsString(loginRequest)))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.accessToken").exists())
      .andExpect(jsonPath("$.username").value("loginuser"));
  }
}
