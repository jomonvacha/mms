package com.roots.mms.dto.request;

import jakarta.validation.constraints.NotNull;

public class UpdateUserStatusRequest {
    @NotNull
    private Boolean active;

    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }
}

