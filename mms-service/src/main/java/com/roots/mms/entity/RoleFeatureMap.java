package com.roots.mms.entity;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Maps a role to its granted feature codes. One row per role in the parent
 * {@code role_feature_maps} table; the actual Set<String> of feature codes is
 * stored in the child {@code role_features} table via {@link ElementCollection}.
 */
@Entity
@Table(name = "role_feature_maps")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class RoleFeatureMap {

    // App-assigned id — see User.java for the rationale.
    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @PrePersist
    void ensureId() { if (id == null) id = UUID.randomUUID(); }

    /**
     * Role name (matches {@code roles.name}). Named {@code role} rather than
     * {@code roleName} to preserve the legacy API shape.
     */
    @Column(name = "role_name", length = 64, nullable = false, unique = true)
    private String role;

    /**
     * Feature codes granted to this role. EAGER so FeatureService.getMatrix()
     * can stream through without per-row lazy fetches.
     */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "role_features",
            joinColumns = @JoinColumn(name = "role_feature_map_id"))
    @Column(name = "feature_code", length = 64, nullable = false)
    @Builder.Default
    private Set<String> featureCodes = new HashSet<>();
}
