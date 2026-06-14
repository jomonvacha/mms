package com.roots.mms.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Category-scoped tier definition. Tiers are NOT universal — each tier
 * belongs to exactly one category, and the same code (e.g. FREE) may exist
 * under multiple categories with independent settings. The compound unique
 * index on (categoryCode, tierCode) enforces that constraint.
 */
@Entity
@Table(
        name = "membership_tiers",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_membership_tiers_cat_tier",
                columnNames = {"category_code", "tier_code"}))
@EntityListeners(AuditingEntityListener.class)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class MembershipTierConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "category_code", length = 32, nullable = false)
    private String categoryCode;

    @Column(name = "tier_code", length = 32, nullable = false)
    private String tierCode;

    @Column(name = "display_name", length = 128)
    private String displayName;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @Builder.Default
    @Column(name = "enabled", nullable = false)
    private Boolean enabled = true;

    @Builder.Default
    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;

    /** True for the seeded defaults. System tiers cannot be deleted. */
    @Builder.Default
    @Column(name = "system", nullable = false)
    private Boolean system = false;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void prePersist() {
        if (updatedAt == null) updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
