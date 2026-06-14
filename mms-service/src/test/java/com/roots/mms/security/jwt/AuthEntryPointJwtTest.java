package com.roots.mms.security.jwt;

import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AuthEntryPointJwtTest {

    private AuthEntryPointJwt entryPoint;
    private ObjectMapper mapper;

    @BeforeEach
    void setUp() {
        entryPoint = new AuthEntryPointJwt();
        mapper = new ObjectMapper();
    }

    @Test
    void commence_setsStatus401() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        AuthenticationException ex = new BadCredentialsException("bad creds");

        entryPoint.commence(request, response, ex);

        assertThat(response.getStatus()).isEqualTo(401);
    }

    @Test
    void commence_setsContentTypeJson() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        entryPoint.commence(request, response, new BadCredentialsException("x"));

        assertThat(response.getContentType()).isEqualTo(MediaType.APPLICATION_JSON_VALUE);
    }

    @Test
    void commence_bodyContainsStatusAndErrorFields() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setServletPath("/api/users/me");
        MockHttpServletResponse response = new MockHttpServletResponse();

        entryPoint.commence(request, response, new BadCredentialsException("x"));

        @SuppressWarnings("unchecked")
        Map<String, Object> body = mapper.readValue(response.getContentAsString(), Map.class);

        assertThat(body.get("status")).isEqualTo(401);
        assertThat(body.get("error")).isEqualTo("Unauthorized");
        assertThat(body.get("message")).asString().isNotBlank();
        assertThat(body.get("path")).isEqualTo("/api/users/me");
    }

    @Test
    void commence_echoesServletPath() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setServletPath("/api/protected/resource");
        MockHttpServletResponse response = new MockHttpServletResponse();

        entryPoint.commence(request, response, new BadCredentialsException("x"));

        @SuppressWarnings("unchecked")
        Map<String, Object> body = mapper.readValue(response.getContentAsString(), Map.class);
        assertThat(body.get("path")).isEqualTo("/api/protected/resource");
    }
}
