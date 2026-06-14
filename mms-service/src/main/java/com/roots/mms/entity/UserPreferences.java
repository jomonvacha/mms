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
 * Per-user UI preferences, 1:1 with the owning user. PK IS the user's UUID —
 * no surrogate identity.
 */
@Entity
@Table(name = "user_preferences")
@Getter
@Setter
@NoArgsConstructor
public class UserPreferences {

    /** userId is both the row PK and the FK reference to users. */
    @Id
    @Column(name = "user_id", updatable = false, nullable = false)
    private UUID userId;

    @Column(name = "theme", length = 16, nullable = false)
    private String theme = "system";        // system | light | dark

    @Column(name = "language", length = 16, nullable = false)
    private String language = "en";

    @Column(name = "country", length = 32)
    private String country;

    @Column(name = "timezone", length = 64)
    private String timezone;

    @Column(name = "email_notifications", nullable = false)
    private Boolean emailNotifications = Boolean.TRUE;

    @Column(name = "navbar_display", length = 16, nullable = false)
    private String navbarDisplay = "avatar"; // avatar | name
}
