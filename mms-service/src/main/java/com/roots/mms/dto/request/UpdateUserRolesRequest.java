package com.roots.mms.dto.request;

import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateUserRolesRequest {
  @NotEmpty
  private List<String> roles; // e.g., ["ROLE_MEMBER","ROLE_ADMIN"]
}
