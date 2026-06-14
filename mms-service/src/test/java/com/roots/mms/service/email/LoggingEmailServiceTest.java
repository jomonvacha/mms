package com.roots.mms.service.email;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

/**
 * Pure unit test (no Spring) for the in-memory dev/test email sink. Keeps
 * the cap behaviour, ordering, and lookup helpers honest so the
 * password-reset / email-verification integration tests can rely on them.
 */
class LoggingEmailServiceTest {

    private LoggingEmailService service;

    @BeforeEach
    void setUp() {
        service = new LoggingEmailService();
    }

    @Test
    void send_null_throws() {
        assertThatNullPointerException().isThrownBy(() -> service.send(null));
        assertThat(service.recent()).isEmpty();
    }

    @Test
    void recent_initiallyEmpty() {
        assertThat(service.recent()).isEmpty();
    }

    @Test
    void recent_returnsNewestFirst() {
        OutgoingEmail first = OutgoingEmail.of("a@example.com", "Hi A", "Body A");
        OutgoingEmail second = OutgoingEmail.of("b@example.com", "Hi B", "Body B");

        service.send(first);
        service.send(second);

        assertThat(service.recent()).containsExactly(second, first);
    }

    @Test
    void recent_capsAtMaxStored() {
        // The class caps in-memory storage at 50; sending 60 should retain the
        // most recent 50 with the oldest dropped.
        for (int i = 0; i < 60; i++) {
            service.send(OutgoingEmail.of("user@example.com", "Subj " + i, "Body " + i));
        }

        List<OutgoingEmail> recent = service.recent();
        assertThat(recent).hasSize(50);
        // Newest first → first entry has the highest index.
        assertThat(recent.get(0).subject()).isEqualTo("Subj 59");
        // Oldest retained should be the one at index 10 (60 - 50 = 10).
        assertThat(recent.get(49).subject()).isEqualTo("Subj 10");
    }

    @Test
    void lastTo_unknownRecipient_returnsNull() {
        service.send(OutgoingEmail.of("known@example.com", "Subj", "Body"));

        assertThat(service.lastTo("ghost@example.com")).isNull();
    }

    @Test
    void lastTo_caseInsensitive() {
        OutgoingEmail email = OutgoingEmail.of("Mixed@Example.com", "Subj", "Body");
        service.send(email);

        assertThat(service.lastTo("mixed@example.com")).isSameAs(email);
        assertThat(service.lastTo("MIXED@EXAMPLE.COM")).isSameAs(email);
    }

    @Test
    void lastTo_returnsMostRecentMatch() {
        service.send(OutgoingEmail.of("user@example.com", "First",  "1"));
        service.send(OutgoingEmail.of("other@example.com", "Other", "2"));
        OutgoingEmail latestForUser = OutgoingEmail.of("user@example.com", "Second", "3");
        service.send(latestForUser);

        assertThat(service.lastTo("user@example.com")).isSameAs(latestForUser);
    }

    @Test
    void recent_returnedListIsImmutable() {
        service.send(OutgoingEmail.of("a@example.com", "Subj", "Body"));

        List<OutgoingEmail> snapshot = service.recent();

        // Even though the deque is concurrent, recent() returns a copy. Mutating
        // the snapshot must not affect future calls.
        assertThat(snapshot).isNotEmpty();
        assertThat(snapshot).isUnmodifiable();
    }

    @Test
    void clear_emptiesTheBuffer() {
        service.send(OutgoingEmail.of("a@example.com", "1", "1"));
        service.send(OutgoingEmail.of("b@example.com", "2", "2"));

        service.clear();

        assertThat(service.recent()).isEmpty();
        assertThat(service.lastTo("a@example.com")).isNull();
    }
}
