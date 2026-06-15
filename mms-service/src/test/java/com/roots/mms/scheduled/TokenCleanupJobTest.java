package com.roots.mms.scheduled;

import com.roots.mms.repository.UserSessionRepository;
import com.roots.mms.repository.VerificationTokenRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TokenCleanupJobTest {

    private VerificationTokenRepository tokenRepo;
    private UserSessionRepository sessionRepo;
    private TokenCleanupJob job;

    @BeforeEach
    void setUp() {
        tokenRepo = mock(VerificationTokenRepository.class);
        sessionRepo = mock(UserSessionRepository.class);
        when(sessionRepo.deleteAllByExpiresAtBefore(any())).thenReturn(0);
        job = new TokenCleanupJob(tokenRepo, sessionRepo, new SimpleMeterRegistry());
    }

    @Test
    void sweepExpiredTokens_callsRepoWithCutoffApproximatelyNow() {
        when(tokenRepo.deleteAllByExpiresAtBefore(any())).thenReturn(0);

        Instant before = Instant.now();

        job.sweepExpiredTokens();

        verify(tokenRepo).deleteAllByExpiresAtBefore(argThat(cutoff -> {
            long diffMs = Math.abs(cutoff.toEpochMilli() - before.toEpochMilli());
            return diffMs < 2_000;
        }));
    }

    @Test
    void sweepExpiredTokens_zeroDeleted_completesWithoutError() {
        when(tokenRepo.deleteAllByExpiresAtBefore(any())).thenReturn(0);

        job.sweepExpiredTokens();

        verify(tokenRepo).deleteAllByExpiresAtBefore(any());
    }

    @Test
    void sweepExpiredTokens_positiveDeleted_completesWithoutError() {
        when(tokenRepo.deleteAllByExpiresAtBefore(any())).thenReturn(7);

        job.sweepExpiredTokens();

        verify(tokenRepo).deleteAllByExpiresAtBefore(any());
    }
}
