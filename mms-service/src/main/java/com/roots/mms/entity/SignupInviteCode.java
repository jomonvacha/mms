package com.roots.mms.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
 * Admin-managed invite code consumed during IDFY user signup. Codes carry an
 * optional {@link #maxUses} (null = unlimited), an {@link #expiresAt}, and an
 * {@link #active} flag.
 */
@Entity
@Table(name = "signup_invite_codes")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SignupInviteCode {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "code", length = 64, nullable = false, unique = true)
    private String code;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    /** Null = unlimited uses. */
    @Column(name = "max_uses")
    private Integer maxUses;

    /** Defaults to 0 on create. Incremented on consumption. */
    @Column(name = "used_count", nullable = false)
    private Integer usedCount;

    /** Null = never expires. */
    @Column(name = "expires_at")
    private Instant expiresAt;

    /** Soft-disable without deleting audit history. */
    @Column(name = "active", nullable = false)
    private Boolean active;

    @Column(name = "created_by", length = 128)
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
