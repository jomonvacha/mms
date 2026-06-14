package com.roots.mms.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.UUID;

/**
 * Binds a single {@link AiModel} to a specific (category, tier) pair. The
 * presence of a binding means "this model is included for this tier". Tiers
 * without a binding for a given model either hide it or show it locked —
 * controlled globally via the binding's {@link AiModel.LockStyle} on the
 * owning model (NB: lock style is model-wide here, not per-binding, to keep
 * the UI consistent across tiers).
 */
@Entity
@Table(
        name = "ai_model_tier_bindings",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_ai_model_tier_binding",
                columnNames = {"model_code", "category_code", "tier_code"}))
@EntityListeners(AuditingEntityListener.class)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class AiModelTierBinding {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "model_code", length = 64, nullable = false)
    private String modelCode;

    @Column(name = "category_code", length = 32, nullable = false)
    private String categoryCode;

    @Column(name = "tier_code", length = 32, nullable = false)
    private String tierCode;

    /**
     * True when this binding marks the tier default — the model used if the
     * persona has no explicit preferredModel. Exactly one binding per
     * (category, tier) should carry this flag; the service layer enforces
     * "last write wins" when a new default is set.
     */
    @Builder.Default
    @Column(name = "is_default", nullable = false)
    private Boolean isDefault = false;

    /** Optional per-binding label override, e.g. show "Standard" for a tier. */
    @Column(name = "label_override", length = 128)
    private String labelOverride;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "created_by", length = 64)
    private String createdBy;

    @Column(name = "updated_by", length = 64)
    private String updatedBy;
}
