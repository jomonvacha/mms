package com.roots.mms.service.email;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
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
        mailSender.send(msg);
        log.info("Sent email via SMTP to {} (subject: {})", email.to(), email.subject());
    }
}
