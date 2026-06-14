package com.roots.mms.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * Binary avatar blob, 1:1 with the owning user. The PK IS the user's UUID so
 * the row is uniquely keyed on the user — no surrogate.
 */
@Entity
@Table(name = "user_avatars")
@Getter
@Setter
@NoArgsConstructor
public class UserAvatar {

    /** userId is both the row PK and the FK reference to users. */
    @Id
    @Column(name = "user_id", updatable = false, nullable = false)
    private UUID userId;

    @Column(name = "content_type", length = 64, nullable = false)
    private String contentType;

    @Column(name = "data", nullable = false)
    private byte[] data;
}
