package com.roots.mms.service.email;

import java.time.Instant;

/**
 * Simple value object for a plain-text email. Both the SMTP and log-based
 * {@link EmailService} implementations consume this.
 */
public record OutgoingEmail(
        String to,
        String subject,
        String body,
        Instant sentAt
) {
    public static OutgoingEmail of(String to, String subject, String body) {
        return new OutgoingEmail(to, subject, body, Instant.now());
    }
}
