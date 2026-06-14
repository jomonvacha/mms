package com.roots.mms.dto.response;

import com.roots.mms.entity.MembershipStatus;
import com.roots.mms.entity.MembershipType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MemberResponse {
    private String id;
    private String membershipId;
    private UserResponse user;
    private MembershipType membershipType;
    /** Governance-managed category (e.g. PERSONAL, EDUCATION, ENTERPRISE). */
    private String categoryCode;
    /** Category-scoped tier (e.g. FREE, PRO, MAX). */
    private String tierCode;
    private MembershipStatus status;
    private LocalDate membershipStartDate;
    private LocalDate membershipEndDate;
    private String notes;
    private Boolean isActive;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
