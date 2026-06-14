package com.roots.mms.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Flat view of entitlements resolved for a specific member. Values are the
 * stringified canonical form; UI and backend callers parse as appropriate
 * (BOOLEAN → "true"/"false", INTEGER → decimal string, STRING → passthrough).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ResolvedEntitlementsResponse {
    private String userId;
    private String categoryCode;
    private String tierCode;
    private Boolean categoryEnabled;
    private Boolean tierEnabled;
    /** key → stringified value. */
    private Map<String, String> entitlements;
}
