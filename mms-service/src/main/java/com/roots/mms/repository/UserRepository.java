package com.roots.mms.repository;

import com.roots.mms.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByUsername(String username);

    Optional<User> findByEmail(String email);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    Optional<User> findByUsernameOrEmail(String username, String email);

    /** Accounts whose deletion grace window has elapsed — ready to be purged. */
    List<User> findByPendingDeletionTrueAndDeletionScheduledAtBefore(LocalDateTime cutoff);
}
