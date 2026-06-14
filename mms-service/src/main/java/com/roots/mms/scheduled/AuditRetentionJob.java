package com.roots.mms.scheduled;

import com.roots.mms.repository.AiModelAuditRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

/**
 * Replaces the Mongo TTL on {@code ai_model_audit.at} (1-year retention).
 *
 * <p>Audit rows are append-only and safe to delete past the retention
 * window. The cutoff is always computed relative to "now" so a late sweep
 * still trims the right slice.
 *
 * <p>Observability: Metric names {@code mms.audit.retention.runs} (Timer)
 * and {@code mms.audit.retention.deleted} (Counter).
 */
@Component
@Profile("!migration")
@Slf4j
public class AuditRetentionJob {

    /** Keep one year of audit history, matching the old Mongo TTL. */
    private static final Duration RETENTION = Duration.ofDays(365);

    private final AiModelAuditRepository audits;
    private final Timer runTimer;
    private final Counter deletedCounter;

    public AuditRetentionJob(AiModelAuditRepository audits, MeterRegistry meterRegistry) {
        this.audits = audits;
        this.runTimer = Timer.builder("mms.audit.retention.runs")
                .description("Time spent sweeping expired AI model audit rows")
                .register(meterRegistry);
        this.deletedCounter = Counter.builder("mms.audit.retention.deleted")
                .description("Number of audit rows deleted past the retention window")
                .register(meterRegistry);
    }

    /** Daily at 03:15. */
    @Scheduled(cron = "0 15 3 * * *")
    public void sweepOldAuditEntries() {
        Instant cutoff = Instant.now().minus(RETENTION);
        int removed = runTimer.record(() -> audits.deleteAllByAtBefore(cutoff));
        if (removed > 0) {
            deletedCounter.increment(removed);
            log.info("Audit retention: removed {} entries older than {} ({})",
                    removed, RETENTION, cutoff);
        }
    }
}
