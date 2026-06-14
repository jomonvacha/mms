package com.roots.mms.entity;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "members")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
public class Member {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @NotBlank
    @Size(max = 50)
    @Column(name = "membership_id", length = 50, nullable = false, unique = true)
    private String membershipId;

    /** Authoritative FK into {@code users.id}. */
    @Column(name = "user_id", nullable = false, unique = true)
    private UUID userId;

    /**
     * Denormalized user snapshot embedded for display and search. Populated on
     * create/update; kept in sync via {@code MemberService.syncDenormalizedUserSnapshot}.
     *
     * <p>Flattened onto the {@code user_snapshot_*} columns on {@code members}
     * via {@link AttributeOverride}. The legacy JSON field name stays {@code user}
     * so existing MemberResponse mapping continues to work.
     */
    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "username",    column = @Column(name = "user_snapshot_username",   length = 20)),
            @AttributeOverride(name = "email",       column = @Column(name = "user_snapshot_email",      length = 254)),
            @AttributeOverride(name = "firstName",   column = @Column(name = "user_snapshot_first_name", length = 50)),
            @AttributeOverride(name = "lastName",    column = @Column(name = "user_snapshot_last_name",  length = 50)),
            @AttributeOverride(name = "phoneNumber", column = @Column(name = "user_snapshot_phone",      length = 15)),
            @AttributeOverride(name = "active",      column = @Column(name = "user_snapshot_active"))
    })
    private UserSummary user;

    @Enumerated(EnumType.STRING)
    @Column(name = "membership_type", length = 32, nullable = false)
    private MembershipType membershipType;

    /**
     * Governance-managed category (e.g. PERSONAL, EDUCATION, ENTERPRISE). Null on
     * legacy member records until the startup backfill runs.
     */
    @Column(name = "category_code", length = 32)
    private String categoryCode;

    /**
     * Tier inside the category (e.g. FREE, PRO, MAX). Tiers are category-scoped
     * — the same code can exist under multiple categories with independent
     * settings and entitlements.
     */
    @Column(name = "tier_code", length = 32)
    private String tierCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 16, nullable = false)
    private MembershipStatus status;

    @Column(name = "membership_start_date")
    private LocalDate membershipStartDate;

    @Column(name = "membership_end_date")
    private LocalDate membershipEndDate;

    @Size(max = 500)
    @Column(name = "notes", length = 500)
    private String notes;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public Member(String membershipId, UserSummary user, MembershipType membershipType) {
        this.membershipId = membershipId;
        // UserSummary.id is a String-form UUID; parse back to UUID for the FK.
        this.userId = user != null && user.getId() != null ? UUID.fromString(user.getId()) : null;
        this.user = user;
        this.membershipType = membershipType;
        this.status = MembershipStatus.ACTIVE;
        this.membershipStartDate = LocalDate.now();
    }
}
