package com.roots.mms.entity;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Embedded;
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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * Immutable audit record for an admin mutation on the AI model registry.
 * Written by the controller layer on every successful write; never mutated
 * after creation. Retention is handled by a scheduled delete job
 * (see {@code AiModelAuditRepository.deleteAllByAtBefore}).
 */
@Entity
@Table(name = "ai_model_audit")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class AiModelAudit {

    public enum Action {
        PROVIDER_CREATED, PROVIDER_UPDATED, PROVIDER_DELETED,
        MODEL_CREATED, MODEL_UPDATED, MODEL_STATUS_CHANGED, MODEL_DELETED,
        BINDING_CREATED, BINDING_UPDATED, BINDING_DELETED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    /** Username/email of the acting admin. */
    @Column(name = "actor_id", length = 128)
    private String actorId;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", length = 48, nullable = false)
    private Action action;

    /** Model affected — always set, even on binding events, for easy filtering. */
    @Column(name = "model_code", length = 64)
    private String modelCode;

    /** Populated for binding-scoped actions. */
    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "categoryCode", column = @Column(name = "binding_category_code", length = 32)),
            @AttributeOverride(name = "tierCode",     column = @Column(name = "binding_tier_code",     length = 32))
    })
    private BindingRef binding;

    /** Pre-mutation JSON snapshot. Null on create events. Stored as JSONB. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "before_json", columnDefinition = "jsonb")
    private String beforeJson;

    /** Post-mutation JSON snapshot. Null on delete events. Stored as JSONB. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "after_json", columnDefinition = "jsonb")
    private String afterJson;

    @Column(name = "ip", length = 64)
    private String ip;

    @Column(name = "user_agent", length = 512)
    private String userAgent;

    @Column(name = "at", nullable = false)
    private Instant at;

    /** Compact reference to a (category, tier) pair. */
    @Embeddable
    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class BindingRef {
        private String categoryCode;
        private String tierCode;
    }
}
