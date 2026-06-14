package com.roots.mms.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpsertTierEntitlementRequest {
    @NotBlank
    private String entitlementKey;

    /** Stringified value — parsed according to the entitlement's ValueType. */
    @Size(max = 200)
    private String value;
}
