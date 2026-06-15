package com.roots.mms.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/** A single active sign-in session, as shown in account-security settings. */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SessionResponse {
    private String id;
    private String deviceLabel;
    private String userAgent;
    private String ip;
    private Instant createdAt;
    private Instant lastActiveAt;
    private Instant expiresAt;
    /** True for the session that made this request. */
    private boolean current;
}
