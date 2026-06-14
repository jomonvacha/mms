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

import java.util.UUID;

@Entity
@Table(name = "enforcement_rules")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class EnforcementRule {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    /** The rule text injected into the system prompt. */
    @Column(name = "text", columnDefinition = "text", nullable = false)
    private String text;

    /** "SYSTEM" (invisible to persona owners) or "OPTIONAL" (persona owners can toggle). */
    @Column(name = "tier", length = 16, nullable = false)
    private String tier;

    /** Category: security, formatting, compliance, style, behavior. */
    @Column(name = "category", length = 64)
    private String category;

    /** For OPTIONAL rules — whether enabled by default for new personas. */
    @Builder.Default
    @Column(name = "enabled_by_default", nullable = false)
    private Boolean enabledByDefault = true;

    /** Display/injection order. */
    @Builder.Default
    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;

    /** Admin can deactivate without deleting. */
    @Builder.Default
    @Column(name = "active", nullable = false)
    private Boolean active = true;
}
