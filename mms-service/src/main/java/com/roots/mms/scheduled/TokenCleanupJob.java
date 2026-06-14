package com.roots.mms.scheduled;

import com.roots.mms.repository.VerificationTokenRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Replaces the Mongo TTL index on {@code verification_tokens.expires_at}.
 *
 * <p>Postgres has no per-row TTL — we sweep expired rows hourly. If the job
 * misses a run, nothing breaks; expired tokens just linger until the next
 * sweep. Deletion is idempotent and does not cascade to anything sensitive.
 *
 * <p>Observability: each run records a Micrometer timer and a counter of
 * rows deleted so ops can watch sweep latency + reap rate on the dashboard.
 * Metric names: {@code mms.tokens.cleanup.runs} (Timer), {@code mms.tokens.cleanup.deleted} (Counter).
 */
@Component
@Profile("!migration")
@Slf4j
public class TokenCleanupJob {

    private final VerificationTokenRepository tokens;
    private final Timer runTimer;
    private final Counter deletedCounter;

    public TokenCleanupJob(VerificationTokenRepository tokens, MeterRegistry meterRegistry) {
        this.tokens = tokens;
        this.runTimer = Timer.builder("mms.tokens.cleanup.runs")
                .description("Time spent sweeping expired verification tokens")
                .register(meterRegistry);
        this.deletedCounter = Counter.builder("mms.tokens.cleanup.deleted")
                .description("Number of expired verification tokens deleted")
                .register(meterRegistry);
    }

    /** Every hour at :05 past the hour. */
    @Scheduled(cron = "0 5 * * * *")
    public void sweepExpiredTokens() {
        Instant cutoff = Instant.now();
        int removed = runTimer.record(() -> tokens.deleteAllByExpiresAtBefore(cutoff));
        if (removed > 0) {
            deletedCounter.increment(removed);
            log.info("Token cleanup: removed {} expired verification tokens (cutoff={})", removed, cutoff);
        }
    }
}
