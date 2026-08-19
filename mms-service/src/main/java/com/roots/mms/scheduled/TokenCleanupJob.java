package com.roots.mms.scheduled;

import com.roots.mms.repository.UserSessionRepository;
import com.roots.mms.repository.VerificationTokenRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Replaces the Mongo TTL index on {@code verification_tokens.expires_at}.
 *
 * <p>Postgres has no per-row TTL — we sweep expired rows hourly. If the job
 * misses a run, nothing breaks; expired tokens just linger until the next
 * sweep. Deletion is idempotent and does not cascade to anything sensitive.
 *
 * <p>Also sweeps expired {@code user_sessions} rows, which have the same
 * no-TTL problem and the same hourly cadence.
 *
 * <p>Observability: each run records a Micrometer timer covering the whole
 * sweep, plus a per-table counter of rows deleted, so ops can watch sweep
 * latency and reap rate for both tables on the dashboard. Metric names:
 * {@code mms.tokens.cleanup.runs} (Timer), {@code mms.tokens.cleanup.deleted}
 * (Counter), {@code mms.sessions.cleanup.deleted} (Counter).
 */
@Component
@Profile("!migration")
@Slf4j
public class TokenCleanupJob {

    private final VerificationTokenRepository tokens;
    private final UserSessionRepository sessions;
    private final Timer runTimer;
    private final Counter deletedCounter;
    private final Counter sessionsDeletedCounter;

    public TokenCleanupJob(VerificationTokenRepository tokens, UserSessionRepository sessions,
                           MeterRegistry meterRegistry) {
        this.tokens = tokens;
        this.sessions = sessions;
        this.runTimer = Timer.builder("mms.tokens.cleanup.runs")
                .description("Time spent sweeping expired verification tokens")
                .register(meterRegistry);
        this.deletedCounter = Counter.builder("mms.tokens.cleanup.deleted")
                .description("Number of expired verification tokens deleted")
                .register(meterRegistry);
        this.sessionsDeletedCounter = Counter.builder("mms.sessions.cleanup.deleted")
                .description("Number of expired user sessions deleted")
                .register(meterRegistry);
    }

    /** Every hour at :05 past the hour. */
    @Scheduled(cron = "0 5 * * * *")
    @Transactional
    public void sweepExpiredTokens() {
        Instant cutoff = Instant.now();
        // Both deletes sit inside the timer: they share one transaction, so
        // timing only the first would report a fraction of the sweep's cost and
        // leave session reaping invisible on the dashboard entirely.
        int[] removed = runTimer.record(() -> new int[]{
                tokens.deleteAllByExpiresAtBefore(cutoff),
                sessions.deleteAllByExpiresAtBefore(cutoff)
        });
        int removedTokens = removed[0];
        int removedSessions = removed[1];
        if (removedTokens > 0) {
            deletedCounter.increment(removedTokens);
            log.info("Token cleanup: removed {} expired verification tokens (cutoff={})", removedTokens, cutoff);
        }
        if (removedSessions > 0) {
            sessionsDeletedCounter.increment(removedSessions);
            log.info("Session cleanup: removed {} expired sessions (cutoff={})", removedSessions, cutoff);
        }
    }
}
