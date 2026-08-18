package com.roots.mms.health;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Component;

@Component("mail")
@ConditionalOnBean(JavaMailSender.class)
public class MailHealthIndicator implements HealthIndicator {
    private final JavaMailSender sender;

    public MailHealthIndicator(JavaMailSender sender) {
        this.sender = sender;
    }

    @Override
    public Health health() {
        if (!(sender instanceof JavaMailSenderImpl impl)) {
            return Health.unknown().withDetail("reason", "Unsupported JavaMailSender type").build();
        }
        try {
            impl.testConnection();
            return Health.up().withDetail("host", impl.getHost()).withDetail("port", impl.getPort()).build();
        } catch (Exception ex) {
            return Health.down(ex).withDetail("host", impl.getHost()).withDetail("port", impl.getPort()).build();
        }
    }
}
