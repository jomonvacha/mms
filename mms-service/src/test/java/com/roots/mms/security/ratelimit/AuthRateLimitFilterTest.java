package com.roots.mms.security.ratelimit;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class AuthRateLimitFilterTest {

    private AuthRateLimitFilter filter;
    private FilterChain chain;

    @BeforeEach
    void setUp() {
        filter = new AuthRateLimitFilter();
        // Use a small capacity so we can exhaust the bucket in tests quickly
        ReflectionTestUtils.setField(filter, "capacity", 2);
        ReflectionTestUtils.setField(filter, "refillMinutes", 1);
        chain = mock(FilterChain.class);
    }

    private MockHttpServletRequest postTo(String uri) {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", uri);
        req.setServletPath(uri);
        req.setRequestURI(uri);
        req.setRemoteAddr("10.0.0.1");
        return req;
    }

    @Test
    void getRequest_toRateLimitedPath_isPassedThrough() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/auth/signin");
        request.setRequestURI("/api/auth/signin");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        assertThat(response.getStatus()).isNotEqualTo(429);
    }

    @Test
    void postToUnlimitedPath_isPassedThrough() throws Exception {
        MockHttpServletRequest request = postTo("/api/users/me");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        assertThat(response.getStatus()).isNotEqualTo(429);
    }

    @Test
    void postToSignin_underLimit_isPassedThrough() throws Exception {
        MockHttpServletRequest request = postTo("/api/auth/signin");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        assertThat(response.getStatus()).isNotEqualTo(429);
    }

    @Test
    void postToSignin_overLimit_returns429() throws Exception {
        for (int i = 0; i < 2; i++) {
            filter.doFilter(postTo("/api/auth/signin"), new MockHttpServletResponse(), chain);
        }
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(postTo("/api/auth/signin"), response, chain);

        assertThat(response.getStatus()).isEqualTo(429);
    }

    @Test
    void postToSignin_overLimit_setsRetryAfterHeader() throws Exception {
        for (int i = 0; i < 2; i++) {
            filter.doFilter(postTo("/api/auth/signin"), new MockHttpServletResponse(), chain);
        }
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(postTo("/api/auth/signin"), response, chain);

        assertThat(response.getHeader("Retry-After")).isEqualTo("60");
    }

    @Test
    void postToSignin_overLimit_bodyContainsRateLimitError() throws Exception {
        for (int i = 0; i < 2; i++) {
            filter.doFilter(postTo("/api/auth/signin"), new MockHttpServletResponse(), chain);
        }
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(postTo("/api/auth/signin"), response, chain);

        assertThat(response.getContentAsString()).contains("RATE_LIMIT_EXCEEDED");
    }

    @Test
    void rateLimitIsPerIp_differentIpsHaveOwnBuckets() throws Exception {
        MockHttpServletRequest fromIp1 = postTo("/api/auth/signin");
        fromIp1.setRemoteAddr("1.1.1.1");
        MockHttpServletRequest fromIp2 = postTo("/api/auth/signin");
        fromIp2.setRemoteAddr("2.2.2.2");

        for (int i = 0; i < 2; i++) {
            filter.doFilter(fromIp1, new MockHttpServletResponse(), chain);
        }
        // Exhaust ip1's bucket; ip2's bucket is fresh
        MockHttpServletResponse exhaustedResponse = new MockHttpServletResponse();
        filter.doFilter(fromIp1, exhaustedResponse, chain);
        assertThat(exhaustedResponse.getStatus()).isEqualTo(429);

        // ip2 should still get through
        MockHttpServletResponse ip2Response = new MockHttpServletResponse();
        filter.doFilter(fromIp2, ip2Response, chain);
        assertThat(ip2Response.getStatus()).isNotEqualTo(429);
    }

    @Test
    void xForwardedForHeader_usedAsClientIp() throws Exception {
        MockHttpServletRequest req = postTo("/api/auth/signup");
        req.addHeader("X-Forwarded-For", "9.9.9.9, 10.0.0.2");

        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(req, response, chain);

        verify(chain).doFilter(req, response);
    }

    @Test
    void rateLimitApplies_toAllFourLimitedPaths() throws Exception {
        String[] paths = {
            "/api/auth/signin",
            "/api/auth/signup",
            "/api/auth/forgot-password",
            "/api/auth/google-id-token"
        };

        for (String path : paths) {
            AuthRateLimitFilter perPathFilter = new AuthRateLimitFilter();
            ReflectionTestUtils.setField(perPathFilter, "capacity", 1);
            ReflectionTestUtils.setField(perPathFilter, "refillMinutes", 1);

            perPathFilter.doFilter(postTo(path), new MockHttpServletResponse(), chain);
            MockHttpServletResponse response = new MockHttpServletResponse();
            perPathFilter.doFilter(postTo(path), response, chain);

            assertThat(response.getStatus())
                    .as("Expected 429 for path %s", path)
                    .isEqualTo(429);
        }
    }
}
