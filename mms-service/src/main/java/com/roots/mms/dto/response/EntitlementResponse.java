package com.roots.mms.dto.response;

import com.roots.mms.entity.Entitlement;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EntitlementResponse {
    private String id;
    private String key;
    private String displayName;
    private String description;
    private Entitlement.ValueType valueType;
    private String category;
    private String defaultValue;
    private Boolean system;
}
