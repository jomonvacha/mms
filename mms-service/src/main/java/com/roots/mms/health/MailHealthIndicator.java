package com.roots.mms.health;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Component;

// Gated on the spring.mail.host property (not @ConditionalOnBean(JavaMailSender.class)):
// that condition is evaluated during component scanning, which runs before Spring Boot's
// deferred auto-configuration registers the JavaMailSender bean, so it's always false in
// practice. spring.mail.host is the same property MailSenderAutoConfiguration itself keys
// off of to create that bean, so this fires exactly when the bean actually exists.
@Component("mail")
@ConditionalOnProperty(prefix = "spring.mail", name = "host")
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
