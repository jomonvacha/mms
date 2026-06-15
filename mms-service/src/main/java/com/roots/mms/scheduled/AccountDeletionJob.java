package com.roots.mms.scheduled;

import com.roots.mms.entity.User;
import com.roots.mms.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Permanently purges accounts whose self-service deletion grace window has
 * elapsed (see {@code UserService.requestDeletion}). Deletion cascades to the
 * user's members, sessions, preferences, avatar, and verification tokens via
 * the FK {@code ON DELETE CASCADE} constraints.
 *
 * <p>Runs daily; a missed run only delays a purge, never loses or double-deletes
 * (the query re-selects only still-pending, past-due accounts each run).
 */
@Component
@Profile("!migration")
@RequiredArgsConstructor
@Slf4j
public class AccountDeletionJob {

    private final UserRepository userRepository;

    /** Daily at 03:30. */
    @Scheduled(cron = "0 30 3 * * *")
    @Transactional
    public void purgeExpiredDeletions() {
        List<User> due = userRepository.findByPendingDeletionTrueAndDeletionScheduledAtBefore(LocalDateTime.now());
        if (due.isEmpty()) return;
        for (User u : due) {
            log.info("Purging account scheduled for deletion: userId={} (scheduledAt={})",
                    u.getId(), u.getDeletionScheduledAt());
            userRepository.delete(u);
        }
        log.info("Account deletion sweep: purged {} account(s)", due.size());
    }
}
