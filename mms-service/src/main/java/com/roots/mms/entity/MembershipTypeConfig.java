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

/**
 * Admin-managed membership type definition. Replaces the hardcoded MembershipType
 * enum for enterprise flexibility. Admins can create custom types (e.g. FAMILY,
 * LIFETIME) via the admin UI.
 */
@Entity
@Table(name = "membership_types")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class MembershipTypeConfig {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "code", length = 32, nullable = false, unique = true)
    private String code;

    @Column(name = "display_name", length = 128)
    private String displayName;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @Builder.Default
    @Column(name = "system", nullable = false)
    private Boolean system = false;
}
