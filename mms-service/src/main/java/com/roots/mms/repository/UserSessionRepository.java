package com.roots.mms.repository;

import com.roots.mms.entity.UserSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface UserSessionRepository extends JpaRepository<UserSession, UUID> {

    /** Active (not revoked, not expired) sessions for a user, newest first. */
    @Query("""
            SELECT s FROM UserSession s
            WHERE s.userId = :userId AND s.revokedAt IS NULL AND s.expiresAt > :now
            ORDER BY s.lastActiveAt DESC
            """)
    List<UserSession> findActiveByUser(@Param("userId") UUID userId, @Param("now") Instant now);

    long countByUserId(UUID userId);

    boolean existsByUserIdAndUserAgentAndIp(UUID userId, String userAgent, String ip);

    /** Revoke every still-active session for a user except the one given (sign out everywhere else). */
    @Modifying
    @Query("""
            UPDATE UserSession s SET s.revokedAt = :now
            WHERE s.userId = :userId AND s.revokedAt IS NULL AND s.id <> :keepId
            """)
    int revokeAllExcept(@Param("userId") UUID userId, @Param("keepId") UUID keepId, @Param("now") Instant now);

    /** Scheduled retention sweep — drop sessions that expired before the cutoff. */
    @Modifying
    @Query("DELETE FROM UserSession s WHERE s.expiresAt < :cutoff")
    int deleteAllByExpiresAtBefore(@Param("cutoff") Instant cutoff);
}
