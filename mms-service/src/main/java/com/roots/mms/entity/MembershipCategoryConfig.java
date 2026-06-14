package com.roots.mms.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
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
 * Admin-managed membership category definition. The built-in categories
 * (PERSONAL, EDUCATION, ENTERPRISE) are seeded on startup with system=true so
 * they can be enabled or disabled but not renamed or deleted.
 */
@Entity
@Table(name = "membership_categories")
@EntityListeners(AuditingEntityListener.class)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class MembershipCategoryConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    /** Uppercase machine code (e.g. PERSONAL). Immutable once created. */
    @Column(name = "code", length = 32, nullable = false, unique = true)
    private String code;

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

    /** True for the three built-in categories. System categories cannot be deleted. */
    @Builder.Default
    @Column(name = "system", nullable = false)
    private Boolean system = false;

    /**
     * NOTE: V1 SQL does not declare an {@code updated_at} column on
     * {@code membership_categories}, so this field is intentionally not mapped.
     * Retained as a deprecated accessor for source compatibility with legacy
     * callers that read the timestamp.
     */
    @LastModifiedDate
    @jakarta.persistence.Transient
    private LocalDateTime updatedAt;
}
