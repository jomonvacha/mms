package com.roots.mms.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserPreferencesResponse {
    private String theme;
    private String language;
    private boolean emailNotifications;
    private String navbarDisplay;
}
