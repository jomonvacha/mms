package com.roots.mms.service.email;

/**
 * Pluggable email sender. Production uses {@code SmtpEmailService}; local dev
 * uses {@code LoggingEmailService} which stores outgoing messages in memory and
 * exposes them via a dev endpoint so you can test password-reset / verification
 * flows end-to-end without a real SMTP server.
 *
 * <p>Select the implementation with {@code app.email.mode} (values: {@code log},
 * {@code smtp}). Default is {@code log}.
 */
public interface EmailService {
    void send(OutgoingEmail email);
}
