package com.roots.mms.entity;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "users")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
public class User {

    // Application-assigned id. Hibernate's UuidGenerator overrides pre-set
    // values when @GeneratedValue is present, which breaks the data migration
    // tool (it needs to pre-assign UUIDs so other tables' FK columns line up).
    // The @PrePersist below keeps the normal runtime flow (new User + save)
    // working without forcing every caller to mint a UUID up front.
    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @PrePersist
    void ensureId() { if (id == null) id = UUID.randomUUID(); }

    @NotBlank
    @Size(max = 20)
    @Column(name = "username", length = 20, nullable = false, unique = true)
    private String username;

    @NotBlank
    @Size(max = 254)
    @Email
    @Column(name = "email", length = 254, nullable = false, unique = true)
    private String email;

    @Size(max = 120)
    @Column(name = "password", length = 120)
    private String password;

    @NotBlank
    @Size(max = 50)
    @Column(name = "first_name", length = 50, nullable = false)
    private String firstName;

    @NotBlank
    @Size(max = 50)
    @Column(name = "last_name", length = 50, nullable = false)
    private String lastName;

    @Size(max = 15)
    @Column(name = "phone_number", length = 15)
    private String phoneNumber;

    @Column(name = "active", nullable = false)
    private Boolean active = true;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /**
     * Eager because {@code UserDetailsServiceImpl.loadUserByUsername} needs
     * authorities immediately after the session-bound transaction closes.
     */
    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
            name = "user_roles",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id"))
    private Set<Role> roles = new HashSet<>();

    @Enumerated(EnumType.STRING)
    @Column(name = "provider", length = 16, nullable = false)
    private AuthProvider provider = AuthProvider.LOCAL;

    @Column(name = "provider_id", length = 128)
    private String providerId;

    /** Has the user proved they own the email address on the account? */
    @Column(name = "email_verified", nullable = false)
    private Boolean emailVerified = false;

    // ── 2FA (TOTP) ───────────────────────────────────────────────────────────
    /** Base32-encoded TOTP shared secret. Null when 2FA isn't set up. */
    @Column(name = "totp_secret", length = 128)
    private String totpSecret;

    /** True only after the user has confirmed setup with a valid code. */
    @Column(name = "totp_enabled", nullable = false)
    private Boolean totpEnabled = false;

    /**
     * Hashed one-time recovery codes stored as JSONB. Each entry is BCrypt-hashed.
     * Hibernate 6+ maps {@code List<String>} to a JSONB column via
     * {@link JdbcTypeCode}.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "totp_recovery_codes", columnDefinition = "jsonb")
    private List<String> totpRecoveryCodes;

    // ── Account lifecycle ────────────────────────────────────────────────────
    /** True while the account is in the reversible deletion grace window. */
    @Column(name = "pending_deletion", nullable = false)
    private Boolean pendingDeletion = false;

    /** When the account will be purged if the user doesn't cancel. */
    @Column(name = "deletion_scheduled_at")
    private LocalDateTime deletionScheduledAt;

    /** Last time the username changed — backs the change-cooldown check. */
    @Column(name = "username_changed_at")
    private LocalDateTime usernameChangedAt;

    public User(String username, String email, String password, String firstName, String lastName) {
        this.username = username;
        this.email = email;
        this.password = password;
        this.firstName = firstName;
        this.lastName = lastName;
    }
}
