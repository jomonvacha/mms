package com.roots.mms.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * A persisted sign-in session / device record. One row is created per login;
 * the row {@link #id} IS the {@code sid} claim embedded in the access and
 * refresh JWTs, so a presented token can be mapped back to its session for
 * remote revoke ("sign out everywhere") and current-session detection.
 *
 * <p>Sessions self-expire at {@link #expiresAt} (mirrors the refresh-token
 * lifetime) and can be revoked early via {@link #revokedAt}. Both are checked
 * by {@code SessionService.isActive}.
 */
@Entity
@Table(name = "user_sessions")
@Getter
@Setter
@NoArgsConstructor
public class UserSession {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @PrePersist
    void ensureId() { if (id == null) id = UUID.randomUUID(); }

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    /** Human-friendly device summary, e.g. "Chrome on macOS". */
    @Column(name = "device_label", length = 128)
    private String deviceLabel;

    @Column(name = "user_agent", length = 512)
    private String userAgent;

    @Column(name = "ip", length = 64)
    private String ip;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "last_active_at", nullable = false)
    private Instant lastActiveAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;
}
