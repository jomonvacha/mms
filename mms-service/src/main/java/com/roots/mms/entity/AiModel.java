package com.roots.mms.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
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
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.UUID;

/**
 * Registry entry for a single AI model available across the platform. The
 * availability to specific members is expressed separately through
 * {@link AiModelTierBinding} — this record only holds the static metadata.
 */
@Entity
@Table(name = "ai_models")
@EntityListeners(AuditingEntityListener.class)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class AiModel {

    /** Status of a model in the registry. */
    public enum Status {
        /** Model is available for binding and active selection. */
        ENABLED,
        /** Model is temporarily disabled — existing bindings are ignored for new selections. */
        DISABLED,
        /**
         * Model is being retired. Kept available to existing personas but no
         * longer offered to members in the selection UI. Usually paired with
         * {@link #replacesModelCode} so clients can suggest a replacement.
         */
        DEPRECATED
    }

    /**
     * How unavailable models are presented to members.
     *
     * <p>Order matters: {@code LOCK} is declared first so ordinal-0 defaults
     * favor "show with upgrade CTA" (member-visible, drives conversion)
     * rather than "hide entirely" — a failure-safe posture.
     */
    public enum LockStyle {
        /** Show the model with an upgrade CTA — drives conversion. Default. */
        LOCK,
        /** Hide the model from the member's UI entirely. */
        HIDE
    }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    /** Canonical machine code, e.g. "gpt-5.4-nano". Stable over model renames. */
    @Column(name = "code", length = 64, nullable = false, unique = true)
    private String code;

    /** FK to {@link AiModelProvider#getCode()}. */
    @Column(name = "provider_code", length = 64, nullable = false)
    private String providerCode;

    @Column(name = "display_name", length = 128)
    private String displayName;

    /** Short marketing blurb shown on selection cards. */
    @Column(name = "description", columnDefinition = "text")
    private String description;

    /** 1 (lowest) to 4 (frontier). Drives the UI quality hint. */
    @Builder.Default
    @Column(name = "quality_level", nullable = false)
    private Integer qualityLevel = 2;

    /** 1 (cheapest) to 4 (most expensive). Drives the UI cost hint. */
    @Builder.Default
    @Column(name = "cost_level", nullable = false)
    private Integer costLevel = 2;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 16, nullable = false)
    private Status status = Status.ENABLED;

    /** Presentation for tiers that don't include this model. */
    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "lock_style", length = 8, nullable = false)
    private LockStyle lockStyle = LockStyle.LOCK;

    /** Set when {@link Status#DEPRECATED} is applied. */
    @Column(name = "deprecated_at")
    private Instant deprecatedAt;

    /** Optional code of the successor model, used to suggest migrations. */
    @Column(name = "replaces_model_code", length = 64)
    private String replacesModelCode;

    /** Display order in member-facing lists; lower shows first. */
    @Builder.Default
    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 100;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** Member id (username) of the actor who created this record. */
    @Column(name = "created_by", length = 64)
    private String createdBy;

    /** Member id (username) of the actor who last updated this record. */
    @Column(name = "updated_by", length = 64)
    private String updatedBy;
}
