package com.roots.mms.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CategoryResponse {
    private String id;
    private String code;
    private String displayName;
    private String description;
    private Boolean enabled;
    private Integer sortOrder;
    private Boolean system;
    private List<TierResponse> tiers;
}
