package com.roots.mms.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

import java.util.UUID;

@Entity
@Table(
        name = "locale_options",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_locale_options",
                columnNames = {"type", "code"}))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class LocaleOption {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    /** Type: LANGUAGE, COUNTRY, TIMEZONE */
    @Column(name = "type", length = 32, nullable = false)
    private String type;

    /** ISO code (en, US, America/New_York) */
    @Column(name = "code", length = 64, nullable = false)
    private String code;

    /** Display label */
    @Column(name = "label", length = 128)
    private String label;

    /** Sort order */
    @Builder.Default
    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;

    /** Whether this option is available for selection */
    @Builder.Default
    @Column(name = "enabled", nullable = false)
    private Boolean enabled = true;
}
