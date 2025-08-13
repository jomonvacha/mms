package com.roots.mms.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class UpdateUserStatusRequest {
    @NotNull
    private Boolean active;
}
