package com.roots.mms.dto.response;

import com.roots.mms.entity.MembershipStatus;
import com.roots.mms.entity.MembershipType;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
public class MemberResponse {
    private Long id;
    private String membershipId;
    private UserResponse user;
    private MembershipType membershipType;
    private MembershipStatus status;
    private LocalDate membershipStartDate;
    private LocalDate membershipEndDate;
    private String notes;
    private Boolean isActive;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
