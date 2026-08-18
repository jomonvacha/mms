package com.roots.mms.health;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnBean(JavaMailSender.class)
public class MailStartupChecker implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(MailStartupChecker.class);
    private final JavaMailSender sender;

    public MailStartupChecker(JavaMailSender sender) {
        this.sender = sender;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!(sender instanceof JavaMailSenderImpl impl)) {
            log.warn("Mail sender is not JavaMailSenderImpl; skipping SMTP connectivity check");
            return;
        }
        try {
            impl.testConnection();
            log.info("SMTP connectivity OK: {}:{}", impl.getHost(), impl.getPort());
        } catch (Exception ex) {
            log.error("SMTP connectivity FAILED to {}:{} -> {}", impl.getHost(), impl.getPort(), ex.getMessage());
        }
    }
}
