package com.roots.mms.repository;

import com.roots.mms.entity.AiModelAudit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.UUID;

public interface AiModelAuditRepository extends JpaRepository<AiModelAudit, UUID> {
    Page<AiModelAudit> findAllByOrderByAtDesc(Pageable pageable);
    Page<AiModelAudit> findByModelCodeOrderByAtDesc(String modelCode, Pageable pageable);
    Page<AiModelAudit> findByActorIdOrderByAtDesc(String actorId, Pageable pageable);

    /**
     * Scheduled retention sweep — replaces the Mongo TTL index that reaped
     * rows where {@code at} passed. Called by the audit retention job.
     */
    @Modifying
    @Query("DELETE FROM AiModelAudit a WHERE a.at < :cutoff")
    int deleteAllByAtBefore(@Param("cutoff") Instant cutoff);
}
