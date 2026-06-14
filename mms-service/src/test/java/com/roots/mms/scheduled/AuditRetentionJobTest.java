package com.roots.mms.scheduled;

import com.roots.mms.repository.AiModelAuditRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuditRetentionJobTest {

    private AiModelAuditRepository auditRepo;
    private AuditRetentionJob job;

    @BeforeEach
    void setUp() {
        auditRepo = mock(AiModelAuditRepository.class);
        job = new AuditRetentionJob(auditRepo, new SimpleMeterRegistry());
    }

    @Test
    void sweepOldAuditEntries_callsRepoWithCutoffApproximatelyOneYearAgo() {
        when(auditRepo.deleteAllByAtBefore(org.mockito.ArgumentMatchers.any())).thenReturn(0);

        Instant before = Instant.now().minusSeconds(365L * 24 * 60 * 60);

        job.sweepOldAuditEntries();

        verify(auditRepo).deleteAllByAtBefore(argThat(cutoff -> {
            long diffSeconds = Math.abs(cutoff.getEpochSecond() - before.getEpochSecond());
            return diffSeconds < 5;
        }));
    }

    @Test
    void sweepOldAuditEntries_zeroDeleted_noCounterIncrement() {
        when(auditRepo.deleteAllByAtBefore(org.mockito.ArgumentMatchers.any())).thenReturn(0);

        job.sweepOldAuditEntries();

        verify(auditRepo).deleteAllByAtBefore(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void sweepOldAuditEntries_positiveDeleted_completesWithoutError() {
        when(auditRepo.deleteAllByAtBefore(org.mockito.ArgumentMatchers.any())).thenReturn(42);

        job.sweepOldAuditEntries();

        verify(auditRepo).deleteAllByAtBefore(org.mockito.ArgumentMatchers.any());
    }
}
