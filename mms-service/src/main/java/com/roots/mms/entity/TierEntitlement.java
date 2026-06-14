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
 * Binds a single entitlement key to a concrete value for a specific
 * (category, tier) pair. The value is stored as a string to keep the schema
 * flexible; the owning {@link Entitlement} record declares the expected
 * {@code ValueType}, and {@code EntitlementService} parses accordingly when
 * resolving.
 */
@Entity
@Table(
        name = "tier_entitlements",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_tier_entitlements",
                columnNames = {"category_code", "tier_code", "entitlement_key"}))
@EntityListeners(AuditingEntityListener.class)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class TierEntitlement {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "category_code", length = 32, nullable = false)
    private String categoryCode;

    @Column(name = "tier_code", length = 32, nullable = false)
    private String tierCode;

    @Column(name = "entitlement_key", length = 128, nullable = false)
    private String entitlementKey;

    /** Canonical stringified value — parsed by EntitlementService. */
    @Column(name = "value", length = 512)
    private String value;

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
