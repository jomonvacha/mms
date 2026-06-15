package com.roots.mms.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserPreferencesRequest {
    @NotBlank
    @Pattern(regexp = "^(system|light|dark)$")
    private String theme;

    @NotBlank
    private String language;

    private String country;
    private String timezone;

    private boolean emailNotifications;

    @Pattern(regexp = "^(avatar|initials|name)$")
    private String navbarDisplay;

    /**
     * Per-category x per-channel notification matrix, e.g.
     * {@code {"security": {"email": true}, "marketing": {"email": false}}}.
     * Unknown categories/channels are ignored; missing entries fall back to
     * defaults. Null leaves the existing matrix untouched.
     */
    private Map<String, Map<String, Boolean>> notificationPrefs;
}
