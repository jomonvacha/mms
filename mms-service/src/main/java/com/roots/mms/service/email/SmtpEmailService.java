package com.roots.mms.service.email;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/**
 * Real SMTP sender. Active when {@code app.email.mode=smtp}. Requires
 * {@code spring.mail.*} to be configured (host, port, username, password).
 */
@Service
@ConditionalOnProperty(name = "app.email.mode", havingValue = "smtp")
@RequiredArgsConstructor
@Slf4j
public class SmtpEmailService implements EmailService {

    private final JavaMailSender mailSender;

    @Value("${app.email.from:no-reply@roots-mms.local}")
    private String fromAddress;

    @Override
    public void send(OutgoingEmail email) {
        SimpleMailMessage msg = new SimpleMailMessage();
        msg.setFrom(fromAddress);
        msg.setTo(email.to());
        msg.setSubject(email.subject());
        msg.setText(email.body());
        try {
            mailSender.send(msg);
            log.info("Sent email via SMTP to {} (subject: {})", mask(email.to()), email.subject());
        } catch (MailException e) {
            // e.getMessage() alone is often a generic wrapper message (e.g. "Mail
            // server connection failed"); the SMTP server's actual response text
            // lives on the root cause. Callers treat send() as fire-and-forget
            // (signup/reset flows must not fail just because SMTP is down), so we
            // swallow here and rely on this log line for diagnosis.
            String detail = e.getMostSpecificCause().getMessage();
            log.error("Failed to send email to {} (subject: {}): {}", mask(email.to()), email.subject(), detail);
        }
    }

    private static String mask(String s) {
        if (s == null || s.length() < 3) return "***";
        int at = s.indexOf('@');
        if (at > 1) {
            String local = s.substring(0, at);
            String domain = s.substring(at);
            int vis = Math.min(2, local.length());
            return local.substring(0, vis) + "***" + domain;
        }
        int vis = Math.min(2, s.length());
        return s.substring(0, vis) + "***";
    }
}
