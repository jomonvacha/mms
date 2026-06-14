package com.roots.mms.service.email;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * Dev/test email sink: writes every outgoing message to the log and keeps the
 * last N messages in memory so a dev endpoint can retrieve them (useful for
 * automated tests of password-reset / email-verification flows).
 *
 * <p>Active when {@code app.email.mode=log} (the default).
 */
@Service
@ConditionalOnProperty(name = "app.email.mode", havingValue = "log", matchIfMissing = true)
@Slf4j
public class LoggingEmailService implements EmailService {

    private static final int MAX_STORED = 50;
    private final Deque<OutgoingEmail> recent = new ConcurrentLinkedDeque<>();

    @Override
    public void send(OutgoingEmail email) {
        Objects.requireNonNull(email);
        recent.addFirst(email);
        while (recent.size() > MAX_STORED) recent.removeLast();
        log.info("[DEV EMAIL] To: {} | Subject: {}\n{}", email.to(), email.subject(), email.body());
    }

    /** Returns the most recent emails, newest first. */
    public List<OutgoingEmail> recent() {
        return List.copyOf(recent);
    }

    /** Returns the most recent email addressed to the given recipient, or null. */
    public OutgoingEmail lastTo(String recipient) {
        for (OutgoingEmail e : recent) {
            if (recipient.equalsIgnoreCase(e.to())) return e;
        }
        return null;
    }

    public void clear() {
        recent.clear();
    }
}
