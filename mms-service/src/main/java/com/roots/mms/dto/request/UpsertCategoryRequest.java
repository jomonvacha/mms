package com.roots.mms.dto.request;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpsertCategoryRequest {
    /** Uppercase code (A-Z, 0-9, underscore). Required when creating. */
    @Pattern(regexp = "^[A-Z][A-Z0-9_]{1,31}$", message = "Category code must be uppercase A-Z0-9_, 2-32 chars")
    private String code;

    @Size(max = 80)
    private String displayName;

    @Size(max = 400)
    private String description;

    private Boolean enabled;

    private Integer sortOrder;
}
