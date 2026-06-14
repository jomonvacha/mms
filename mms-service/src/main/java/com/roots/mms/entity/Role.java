package com.roots.mms.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * A security role. Name is a unique string code (e.g. "ROLE_ADMIN").
 * The default system roles are seeded on startup; admins can create custom roles
 * via the Roles and Features admin page.
 */
@Entity
@Table(name = "roles")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Role {

    // App-assigned id — see User.java for the rationale. @PrePersist keeps the
    // normal runtime happy; the data migration tool pre-assigns via IdMap.
    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @PrePersist
    void ensureId() { if (id == null) id = UUID.randomUUID(); }

    /** Unique role code, e.g. "ROLE_ADMIN". Always uppercase prefixed with ROLE_. */
    @Column(name = "name", length = 64, nullable = false, unique = true)
    private String name;

    /** Human-readable label, e.g. "Administrator". */
    @Column(name = "display_name", length = 128)
    private String displayName;

    /** Optional description of what this role grants. */
    @Column(name = "description", columnDefinition = "text")
    private String description;

    /** "System" for admin/moderation roles, "Platform" for subscription tiers. */
    @Builder.Default
    @Column(name = "category", length = 64, nullable = false)
    private String category = "System";

    /** Whether this is a system-default role (cannot be deleted). */
    @Builder.Default
    @Column(name = "system", nullable = false)
    private Boolean system = false;

    public Role(ERole eRole) {
        this.name = eRole.name();
        this.system = true;
    }

    public Role(String name) {
        this.name = name;
    }
}
