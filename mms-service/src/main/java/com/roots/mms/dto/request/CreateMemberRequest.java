package com.roots.mms.dto.request;

import com.roots.mms.entity.MembershipType;
import jakarta.validation.constraints.NotBlank;
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
    @NotBlank
    private String userId;

    @NotNull
    private MembershipType membershipType;

    /** Governance-managed category (e.g. PERSONAL, EDUCATION, ENTERPRISE). */
    @Size(max = 32)
    private String categoryCode;

    /** Category-scoped tier (e.g. FREE, PRO, MAX). */
    @Size(max = 32)
    private String tierCode;

    private LocalDate membershipStartDate;

    private LocalDate membershipEndDate;

    @Size(max = 500)
    private String notes;
}
