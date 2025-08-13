package com.roots.mms.dto.request;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
public class UpdateUserRolesRequest {
    @NotEmpty
    private List<String> roles; // e.g., ["ROLE_USER","ROLE_ADMIN"]
}
