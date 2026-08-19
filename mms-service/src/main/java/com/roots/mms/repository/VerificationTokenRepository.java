package com.roots.mms.repository;

import com.roots.mms.entity.VerificationToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface VerificationTokenRepository extends JpaRepository<VerificationToken, UUID> {
    Optional<VerificationToken> findByToken(String token);

    /**
     * Invalidates a user's outstanding token of one type before a new one is
     * issued. Annotated here rather than left to callers: {@code SimpleJpaRepository}
     * is {@code @Transactional(readOnly = true)}, under which this derived delete
     * never flushes — the old token silently survives and stays redeemable.
     */
    @Transactional
    void deleteByUserIdAndType(UUID userId, VerificationToken.TokenType type);

    /**
     * Scheduled retention sweep — replaces the Mongo TTL on {@code expiresAt}.
     * Called by the verification-token cleanup job.
     */
    @Modifying
    @Query("DELETE FROM VerificationToken v WHERE v.expiresAt < :cutoff")
    int deleteAllByExpiresAtBefore(@Param("cutoff") Instant cutoff);
}
