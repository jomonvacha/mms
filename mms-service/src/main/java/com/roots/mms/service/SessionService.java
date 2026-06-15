package com.roots.mms.service;

import com.roots.mms.entity.User;
import com.roots.mms.entity.UserSession;
import com.roots.mms.exception.AuthorizationException;
import com.roots.mms.exception.ResourceNotFoundException;
import com.roots.mms.repository.UserSessionRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Persists and manages sign-in sessions (device registry). Backs the
 * "active sessions + remote revoke" account-security feature.
 *
 * <p>A session's id is embedded as the {@code sid} claim in the access and
 * refresh JWTs; {@link com.roots.mms.security.jwt.AuthTokenFilter} rejects
 * requests whose {@code sid} no longer maps to an active session, making
 * "sign out everywhere" take effect immediately rather than at token expiry.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SessionService {

    private final UserSessionRepository sessionRepository;

    /** Creates and persists a session for a fresh sign-in. */
    @Transactional
    public UserSession create(User user, String userAgent, String ip, long ttlMs) {
        UserSession s = new UserSession();
        s.setUserId(user.getId());
        s.setUserAgent(truncate(userAgent, 512));
        s.setIp(truncate(ip, 64));
        s.setDeviceLabel(deviceLabel(userAgent));
        Instant now = Instant.now();
        s.setCreatedAt(now);
        s.setLastActiveAt(now);
        s.setExpiresAt(now.plusMillis(ttlMs));
        return sessionRepository.save(s);
    }

    /** True when the session exists, is not revoked, and has not expired. */
    @Transactional(readOnly = true)
    public boolean isActive(String sessionId) {
        UUID id = parse(sessionId);
        if (id == null) return false;
        return sessionRepository.findById(id)
                .map(s -> s.getRevokedAt() == null && s.getExpiresAt().isAfter(Instant.now()))
                .orElse(false);
    }

    /** Updates last-active on token refresh; ignored if the session is gone. */
    @Transactional
    public void touch(String sessionId) {
        UUID id = parse(sessionId);
        if (id == null) return;
        sessionRepository.findById(id).ifPresent(s -> {
            s.setLastActiveAt(Instant.now());
            sessionRepository.save(s);
        });
    }

    @Transactional(readOnly = true)
    public List<UserSession> listActive(String userId) {
        return sessionRepository.findActiveByUser(UUID.fromString(userId), Instant.now());
    }

    /** Revokes one session the caller owns. */
    @Transactional
    public void revoke(String userId, String sessionId) {
        UUID id = parse(sessionId);
        if (id == null) throw new ResourceNotFoundException("Session", "id", sessionId);
        UserSession s = sessionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Session", "id", sessionId));
        if (!s.getUserId().equals(UUID.fromString(userId))) {
            throw new AuthorizationException("Session", "revoke");
        }
        if (s.getRevokedAt() == null) {
            s.setRevokedAt(Instant.now());
            sessionRepository.save(s);
        }
    }

    /** Revokes every active session for the user except {@code keepSessionId}. */
    @Transactional
    public int revokeAllExcept(String userId, String keepSessionId) {
        UUID keep = parse(keepSessionId);
        if (keep == null) keep = new UUID(0L, 0L); // sentinel: matches nothing → revoke all
        return sessionRepository.revokeAllExcept(UUID.fromString(userId), keep, Instant.now());
    }

    /**
     * Heuristic for the suspicious-sign-in alert: the device is "new" when the
     * user has signed in before but never from this user-agent + IP pair. The
     * very first sign-in is never flagged.
     */
    @Transactional(readOnly = true)
    public boolean isNewDevice(User user, String userAgent, String ip) {
        long prior = sessionRepository.countByUserId(user.getId());
        if (prior == 0) return false;
        return !sessionRepository.existsByUserIdAndUserAgentAndIp(
                user.getId(), truncate(userAgent, 512), truncate(ip, 64));
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    /** Resolves the client IP, honouring a single X-Forwarded-For hop. */
    public static String clientIp(HttpServletRequest request) {
        if (request == null) return null;
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    /** Best-effort "Browser on OS" label from a user-agent string. */
    public static String deviceLabel(String ua) {
        if (ua == null || ua.isBlank()) return "Unknown device";
        String browser = "Browser";
        if (ua.contains("Edg")) browser = "Edge";
        else if (ua.contains("OPR") || ua.contains("Opera")) browser = "Opera";
        else if (ua.contains("Chrome")) browser = "Chrome";
        else if (ua.contains("Firefox")) browser = "Firefox";
        else if (ua.contains("Safari")) browser = "Safari";

        String os = "Unknown OS";
        if (ua.contains("Windows")) os = "Windows";
        else if (ua.contains("Mac OS") || ua.contains("Macintosh")) os = "macOS";
        else if (ua.contains("Android")) os = "Android";
        else if (ua.contains("iPhone") || ua.contains("iPad") || ua.contains("iOS")) os = "iOS";
        else if (ua.contains("Linux")) os = "Linux";

        return browser + " on " + os;
    }

    private static UUID parse(String id) {
        if (id == null || id.isBlank()) return null;
        try { return UUID.fromString(id); } catch (IllegalArgumentException e) { return null; }
    }

    private static String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() > max ? s.substring(0, max) : s;
    }
}
