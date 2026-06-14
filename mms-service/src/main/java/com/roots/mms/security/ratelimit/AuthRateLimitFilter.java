package com.roots.mms.security.ratelimit;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Rate limits POST {@code /api/auth/signin}, {@code /signup} and {@code /forgot-password}
 * with a per-IP token bucket. Protects against brute-force password attacks and
 * signup/reset-link spam. Uses Bucket4j in-memory; swap for a distributed backend
 * (Redis, Hazelcast) if you run multiple instances.
 *
 * <p>Defaults: 10 attempts per minute per IP — tune via env:
 * <ul>
 *   <li>{@code APP_RATELIMIT_SIGNIN_CAPACITY} — bucket capacity (default 10)</li>
 *   <li>{@code APP_RATELIMIT_SIGNIN_REFILL_MINUTES} — refill period (default 1)</li>
 * </ul>
 */
@Component
@Slf4j
public class AuthRateLimitFilter extends OncePerRequestFilter {

    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    @Value("${app.rate-limit.signin.capacity:30}")
    private int capacity;

    @Value("${app.rate-limit.signin.refill-minutes:1}")
    private int refillMinutes;

    private Bucket bucketFor(String key) {
        return buckets.computeIfAbsent(key, k -> Bucket.builder()
                .addLimit(Bandwidth.simple(capacity, Duration.ofMinutes(refillMinutes)))
                .build());
    }

    private static boolean isRateLimitedPath(String path) {
        return path.equals("/api/auth/signin")
                || path.equals("/api/auth/signup")
                || path.equals("/api/auth/forgot-password")
                || path.equals("/api/auth/google-id-token");
    }

    private static String clientIp(HttpServletRequest request) {
        String xf = request.getHeader("X-Forwarded-For");
        if (xf != null && !xf.isBlank()) {
            // Take the first (original client) IP from the chain
            int comma = xf.indexOf(',');
            return (comma > 0 ? xf.substring(0, comma) : xf).trim();
        }
        String real = request.getHeader("X-Real-IP");
        if (real != null && !real.isBlank()) return real.trim();
        return request.getRemoteAddr();
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain)
            throws ServletException, IOException {
        if (!"POST".equalsIgnoreCase(request.getMethod()) || !isRateLimitedPath(request.getRequestURI())) {
            filterChain.doFilter(request, response);
            return;
        }
        String key = request.getRequestURI() + "|" + clientIp(request);
        Bucket bucket = bucketFor(key);
        if (bucket.tryConsume(1)) {
            filterChain.doFilter(request, response);
            return;
        }
        log.warn("Rate limit hit for {} from {}", request.getRequestURI(), clientIp(request));
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setHeader("Retry-After", String.valueOf(refillMinutes * 60));
        response.getWriter().write(
                "{\"status\":429,\"error\":\"Too Many Requests\",\"errorCode\":\"RATE_LIMIT_EXCEEDED\","
                + "\"message\":\"Too many attempts — please wait a moment and try again.\"}");
    }
}
