package com.roots.mms.dto.request;

import com.roots.mms.entity.MembershipType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateMemberRequest {
  @NotNull
  private Long userId;

  @NotNull
  private MembershipType membershipType;

  private LocalDate membershipStartDate;

  private LocalDate membershipEndDate;

  @Size(max = 500)
  private String notes;
}
