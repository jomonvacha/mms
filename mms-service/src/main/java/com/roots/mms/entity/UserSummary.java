package com.roots.mms.entity;

import jakarta.persistence.Embeddable;
import jakarta.persistence.Transient;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Denormalized user snapshot embedded in Member rows.
 * Enables member search/display without a separate users lookup.
 *
 * <p>Mapped as a JPA {@link Embeddable}. The owning {@link Member} uses
 * {@code @AttributeOverrides} to map these fields onto the flattened
 * {@code user_snapshot_*} columns on the {@code members} table. The {@code id}
 * field is not persisted here — Member owns the authoritative {@code user_id}
 * FK. We retain the field as {@link Transient} so existing code paths that
 * populate and read {@code UserSummary.id} keep compiling.
 */
@Embeddable
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserSummary {
    /**
     * String-form of the owning user's UUID. NOT persisted — the authoritative
     * FK lives on {@code Member.userId}. Populated at sync time so in-memory
     * DTO mapping still works.
     */
    @Transient
    private String id;
    private String username;
    private String email;
    private String firstName;
    private String lastName;
    private String phoneNumber;
    private Boolean active;
}
