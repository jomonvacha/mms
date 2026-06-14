package com.roots.mms.dto.request;

import com.roots.mms.entity.AiModel;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Admin payload for {@code PUT /api/admin/models/{code}}. Every field is
 * optional — unspecified fields leave existing values untouched (PATCH
 * semantics, even though the verb is PUT to match the rest of the surface).
 *
 * <p>{@code code} is not in the body because it's the path variable, and
 * status transitions go through the dedicated PATCH endpoint.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateAiModelRequest {

    @Size(max = 64)
    private String providerCode;

    @Size(max = 120)
    private String displayName;

    @Size(max = 500)
    private String description;

    @Min(1) @Max(4)
    private Integer qualityLevel;

    @Min(1) @Max(4)
    private Integer costLevel;

    private Integer sortOrder;

    private AiModel.LockStyle lockStyle;

    @Size(max = 64)
    private String replacesModelCode;
}
