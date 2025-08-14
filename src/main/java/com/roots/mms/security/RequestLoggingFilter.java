package com.roots.mms.security;

import com.roots.mms.security.services.UserPrincipal;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.lang.NonNull;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@Order(2)
@Slf4j
public class RequestLoggingFilter extends OncePerRequestFilter {

    private static void setUserIdFromSecurityContext() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UserPrincipal up) {
            org.slf4j.MDC.put("userId", String.valueOf(up.getId()));
        }
    }

    private static String resolveClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            int comma = xff.indexOf(',');
            return (comma > 0 ? xff.substring(0, comma) : xff).trim();
        }
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isBlank()) return xRealIp.trim();
        return request.getRemoteAddr();
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        long start = System.nanoTime();
        String method = request.getMethod();
        String uri = request.getRequestURI();
        String query = request.getQueryString();
        String fullPath = query == null ? uri : uri + "?" + query;
        // Put request context into MDC so all logs during the request include it
        org.slf4j.MDC.put("method", method);
        org.slf4j.MDC.put("path", fullPath);
        org.slf4j.MDC.put("clientIp", resolveClientIp(request));

        // If already authenticated (e.g., pre-auth), include userId now
        setUserIdFromSecurityContext();
        try {
            filterChain.doFilter(request, response);
        } finally {
            long elapsedMs = (System.nanoTime() - start) / 1_000_000;
            int status = response.getStatus();
            log.info("{} {} -> {} ({} ms)", method, fullPath, status, elapsedMs);
            org.slf4j.MDC.remove("method");
            org.slf4j.MDC.remove("path");
            org.slf4j.MDC.remove("clientIp");
            org.slf4j.MDC.remove("userId");
        }
    }
}
