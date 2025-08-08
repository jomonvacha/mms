package com.roots.mms.dto.request;

import com.roots.mms.entity.MembershipStatus;
import com.roots.mms.entity.MembershipType;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public class UpdateMemberRequest {
    
    private MembershipType membershipType;
    
    private MembershipStatus status;

    private LocalDate membershipStartDate;

    private LocalDate membershipEndDate;

    @Size(max = 500)
    private String notes;

    private Boolean isActive;

    // Getters and Setters
    public MembershipType getMembershipType() {
        return membershipType;
    }

    public void setMembershipType(MembershipType membershipType) {
        this.membershipType = membershipType;
    }

    public MembershipStatus getStatus() {
        return status;
    }

    public void setStatus(MembershipStatus status) {
        this.status = status;
    }

    public LocalDate getMembershipStartDate() {
        return membershipStartDate;
    }

    public void setMembershipStartDate(LocalDate membershipStartDate) {
        this.membershipStartDate = membershipStartDate;
    }

    public LocalDate getMembershipEndDate() {
        return membershipEndDate;
    }

    public void setMembershipEndDate(LocalDate membershipEndDate) {
        this.membershipEndDate = membershipEndDate;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public Boolean getIsActive() {
        return isActive;
    }

    public void setIsActive(Boolean isActive) {
        this.isActive = isActive;
    }
}
