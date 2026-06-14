package com.roots.mms.dto.request;

import com.roots.mms.entity.AiModel;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Admin payload for {@code POST /api/admin/models}. Intentionally narrower
 * than the {@link AiModel} entity — fields like {@code createdBy},
 * {@code createdAt}, and {@code deprecatedAt} are managed server-side and
 * ignored when clients submit them.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateAiModelRequest {

    @NotBlank
    @Pattern(regexp = "^[a-z0-9][a-z0-9._-]{1,63}$",
             message = "Code must start with a letter/digit and contain only lowercase a-z, 0-9, dot, dash, underscore")
    private String code;

    @NotBlank
    @Size(max = 64)
    private String providerCode;

    @NotBlank
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
