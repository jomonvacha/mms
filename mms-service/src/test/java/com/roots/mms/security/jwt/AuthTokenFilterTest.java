package com.roots.mms.security.jwt;

import com.roots.mms.entity.ERole;
import com.roots.mms.entity.Role;
import com.roots.mms.entity.User;
import com.roots.mms.security.services.UserDetailsServiceImpl;
import com.roots.mms.security.services.UserPrincipal;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthTokenFilterTest {

    private static final String SECRET = "testSecretKey123456789012345678901234567890123456789012345678";

    private JwtUtils jwtUtils;
    private UserDetailsServiceImpl userDetailsService;
    private TokenBlacklistService blacklist;
    private AuthTokenFilter filter;
    private FilterChain chain;

    @BeforeEach
    void setUp() {
        jwtUtils = new JwtUtils();
        ReflectionTestUtils.setField(jwtUtils, "jwtSecret", SECRET);
        ReflectionTestUtils.setField(jwtUtils, "jwtExpirationMs", 3_600_000);
        ReflectionTestUtils.setField(jwtUtils, "jwtRefreshExpirationMs", 7_200_000);

        userDetailsService = mock(UserDetailsServiceImpl.class);
        blacklist = new TokenBlacklistService();
        filter = new AuthTokenFilter(jwtUtils, userDetailsService, blacklist,
                mock(com.roots.mms.service.SessionService.class));
        chain = mock(FilterChain.class);

        SecurityContextHolder.clearContext();
        MDC.clear();
    }

    private UserPrincipal principalFor(String username) {
        User user = new User(username, username + "@test.com", "pw", "First", "Last");
        user.setId(UUID.randomUUID());
        user.setActive(true);
        user.setRoles(Set.of(new Role(ERole.ROLE_MEMBER)));
        return UserPrincipal.create(user);
    }

    @Test
    void validToken_setsAuthenticationInSecurityContext() throws Exception {
        UserPrincipal principal = principalFor("alice");
        when(userDetailsService.loadUserByUsername("alice")).thenReturn(principal);

        String token = jwtUtils.generateTokenFromUsername("alice");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication().getName()).isEqualTo("alice");
        verify(chain).doFilter(request, response);
    }

    @Test
    void validToken_putsUserIdInMdc() throws Exception {
        UserPrincipal principal = principalFor("alice");
        when(userDetailsService.loadUserByUsername("alice")).thenReturn(principal);

        String token = jwtUtils.generateTokenFromUsername("alice");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + token);

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertThat(MDC.get("userId")).isEqualTo(principal.getId());
    }

    @Test
    void noAuthorizationHeader_doesNotSetAuthentication() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(userDetailsService, never()).loadUserByUsername(anyString());
    }

    @Test
    void malformedToken_doesNotSetAuthentication() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer not.a.real.jwt");

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void revokedToken_doesNotSetAuthentication() throws Exception {
        String token = jwtUtils.generateTokenFromUsername("bob");
        blacklist.revoke(token, jwtUtils.getExpirationEpochMs(token));

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + token);

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(userDetailsService, never()).loadUserByUsername(anyString());
    }

    @Test
    void bearerPrefixMissing_doesNotSetAuthentication() throws Exception {
        String token = jwtUtils.generateTokenFromUsername("carol");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", token);

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void filterAlwaysCallsFilterChain_evenOnException() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer invalid-token-!!!!");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
    }
}
