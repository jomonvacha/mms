package com.roots.mms.dto.request;

import com.roots.mms.entity.MembershipStatus;
import com.roots.mms.entity.MembershipType;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@NoArgsConstructor
public class UpdateMemberRequest {
    private MembershipType membershipType;
    private MembershipStatus status;
    private LocalDate membershipStartDate;
    private LocalDate membershipEndDate;
    @Size(max = 500)
    private String notes;
    private Boolean isActive;
}
