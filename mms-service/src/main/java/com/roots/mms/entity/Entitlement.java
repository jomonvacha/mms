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

import java.util.UUID;

/**
 * An entitlement is a single, named capability or limit that features can
 * check against. The definition lives here; the actual value per
 * category-tier pair lives in {@link TierEntitlement}.
 *
 * Keys use a dotted namespace (e.g. {@code idfy.persona.maxCount}) so they
 * can be grouped by product area without relying on string matching.
 */
@Entity
@Table(name = "entitlements")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Entitlement {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "key", length = 128, nullable = false, unique = true)
    private String key;

    @Column(name = "display_name", length = 256)
    private String displayName;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "value_type", length = 16, nullable = false)
    private ValueType valueType = ValueType.BOOLEAN;

    /** Logical grouping for admin UI (e.g. "IDFY Personas", "Sharing"). */
    @Column(name = "category", length = 64)
    private String category;

    /**
     * Fallback value (stringified) when no tier defines this entitlement.
     * For BOOLEAN, use "false". For INTEGER, use "0". For STRING, use "".
     */
    @Builder.Default
    @Column(name = "default_value", length = 256, nullable = false)
    private String defaultValue = "false";

    @Builder.Default
    @Column(name = "system", nullable = false)
    private Boolean system = false;

    public enum ValueType {
        BOOLEAN,
        INTEGER,
        STRING
    }
}
