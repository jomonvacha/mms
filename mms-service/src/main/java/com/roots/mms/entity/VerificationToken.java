package com.roots.mms.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Short-lived one-time token used for password resets and email verification.
 * Stored opaque (random 32 bytes, base64-url encoded). Single-use: marked
 * {@code consumedAt} after redemption.
 *
 * <p>Expired rows are reaped by a scheduled sweep
 * ({@code VerificationTokenRepository.deleteAllByExpiresAtBefore}).
 */
@Entity
@Table(name = "verification_tokens")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VerificationToken {

    public enum TokenType {
        PASSWORD_RESET,
        EMAIL_VERIFICATION,
        EMAIL_CHANGE
    }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "token", length = 128, nullable = false, unique = true)
    private String token;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", length = 32, nullable = false)
    private TokenType type;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "consumed_at")
    private Instant consumedAt;

    /** Pending new address — only populated for {@link TokenType#EMAIL_CHANGE} rows. */
    @Column(name = "new_email", length = 254)
    private String newEmail;
}
