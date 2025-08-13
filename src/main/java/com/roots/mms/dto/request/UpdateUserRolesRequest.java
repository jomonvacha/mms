package com.roots.mms.dto.request;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public class UpdateUserRolesRequest {
    @NotEmpty
    private List<String> roles; // e.g., ["ROLE_USER","ROLE_ADMIN"]

    public List<String> getRoles() {
        return roles;
    }

    public void setRoles(List<String> roles) {
        this.roles = roles;
    }
}

