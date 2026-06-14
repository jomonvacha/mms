package com.roots.mms.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

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
}
