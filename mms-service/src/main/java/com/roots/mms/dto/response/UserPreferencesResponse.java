package com.roots.mms.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserPreferencesResponse {
    private String theme;
    private String language;
    private String country;
    private String timezone;
    private boolean emailNotifications;
    private String navbarDisplay;
    /** Effective per-category x per-channel notification matrix (defaults merged in). */
    private Map<String, Map<String, Boolean>> notificationPrefs;
}
