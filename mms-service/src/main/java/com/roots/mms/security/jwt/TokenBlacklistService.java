package com.roots.mms.security.jwt;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory blacklist of revoked JWTs (used by /api/auth/signout).
 *
 * <p>Tokens self-expire via the JWT's own {@code exp} claim, so we only need to
 * remember them until that expiry. Entries are reaped opportunistically on
 * lookup; this is fine for a single-instance deployment.
 *
 * <p>For multi-instance / production use, replace this with a shared store
 * (Redis with TTL, Hazelcast, etc.) — same interface, different impl.
 */
@Service
@Slf4j
public class TokenBlacklistService {

    private final Map<String, Long> revoked = new ConcurrentHashMap<>();

    /**
     * Revoke a token until the given epoch-millis expiry.
     */
    public void revoke(String token, long expiresAtEpochMs) {
        if (token == null || token.isBlank()) return;
        revoked.put(token, expiresAtEpochMs);
        log.debug("Revoked token (expires at {}); blacklist size = {}", expiresAtEpochMs, revoked.size());
    }

    /**
     * Returns true if the token has been revoked AND has not yet self-expired.
     * Expired blacklist entries are cleaned up lazily on lookup.
     */
    public boolean isRevoked(String token) {
        if (token == null) return false;
        Long expiresAt = revoked.get(token);
        if (expiresAt == null) return false;
        if (System.currentTimeMillis() >= expiresAt) {
            revoked.remove(token);
            return false;
        }
        return true;
    }

    /** Test/admin helper. */
    public int size() {
        return revoked.size();
    }
}
