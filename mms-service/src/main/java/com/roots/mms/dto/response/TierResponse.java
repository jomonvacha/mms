package com.roots.mms.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TierResponse {
    private String id;
    private String categoryCode;
    private String tierCode;
    private String displayName;
    private String description;
    private Boolean enabled;
    private Integer sortOrder;
    private Boolean system;
}
