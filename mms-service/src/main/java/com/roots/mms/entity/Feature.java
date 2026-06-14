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
 * A platform feature/capability that can be granted to roles.
 * Examples: "View Reports", "Export Data", "Manage Users", "API Access".
 */
@Entity
@Table(name = "features")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Feature {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "code", length = 64, nullable = false, unique = true)
    private String code;

    @Column(name = "name", length = 128)
    private String name;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @Column(name = "category", length = 64)
    private String category;

    @Column(name = "icon", length = 64)
    private String icon;

    @Column(name = "enabled", nullable = false)
    private Boolean enabled;
}
