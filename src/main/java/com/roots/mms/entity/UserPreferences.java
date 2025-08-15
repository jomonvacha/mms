package com.roots.mms.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "user_preferences")
@Getter
@Setter
@NoArgsConstructor
public class UserPreferences {

    @Id
    private Long userId;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "theme", length = 10)
    private String theme = "system"; // system | light | dark

    @Column(name = "language", length = 10)
    private String language = "en";

    @Column(name = "email_notifications")
    private Boolean emailNotifications = Boolean.TRUE;
}

