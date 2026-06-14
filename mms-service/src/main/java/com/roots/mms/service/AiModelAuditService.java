package com.roots.mms.service;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.roots.mms.entity.AiModelAudit;
import com.roots.mms.repository.AiModelAuditRepository;
import com.roots.mms.security.SecurityUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * Writes immutable audit records for every mutation on the AI model registry.
 *
 * <p>Controllers call {@link #record(AiModelAudit.Action, String, AiModelAudit.BindingRef, String, Object, HttpServletRequest)}
 * after a successful mutation. The before-state is expected as a pre-captured
 * JSON string so we don't accidentally serialize a post-mutation view of a
 * mutable Mongo entity.
 */
@Service
@Slf4j
public class AiModelAuditService {

    private final AiModelAuditRepository auditRepository;

    /**
     * Local Jackson instance — we don't rely on Spring-managed ObjectMapper
     * because the current mms-service setup doesn't expose one as a bean.
     * Configured to emit compact, JSR-310-aware JSON suitable for audit storage.
     */
    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .setSerializationInclusion(JsonInclude.Include.NON_NULL);

    public AiModelAuditService(AiModelAuditRepository auditRepository) {
        this.auditRepository = auditRepository;
    }

    /** Serializes an arbitrary entity to JSON for storing in an audit record. */
    public String serialize(Object entity) {
        if (entity == null) return null;
        try {
            return objectMapper.writeValueAsString(entity);
        } catch (JsonProcessingException e) {
            log.warn("Could not serialize entity for audit: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Write an audit record. Swallows storage errors and logs them — we never
     * want a failed audit write to abort a successful admin mutation, but we
     * also never want it to fail silently.
     */
    public void record(AiModelAudit.Action action,
                       String modelCode,
                       AiModelAudit.BindingRef binding,
                       String beforeJson,
                       Object afterEntity,
                       HttpServletRequest request) {
        try {
            AiModelAudit audit = AiModelAudit.builder()
                    .actorId(SecurityUtils.getCurrentUserIdOrNull())
                    .action(action)
                    .modelCode(modelCode)
                    .binding(binding)
                    .beforeJson(beforeJson)
                    .afterJson(serialize(afterEntity))
                    .ip(clientIp(request))
                    .userAgent(request != null ? request.getHeader("User-Agent") : null)
                    .at(Instant.now())
                    .build();
            auditRepository.save(audit);
        } catch (Exception e) {
            log.error("[AiModelsAudit] Failed to write audit for action={} modelCode={}",
                    action, modelCode, e);
        }
    }

    // ── Read surface ────────────────────────────────────────────────

    public Page<AiModelAudit> findAll(Pageable pageable) {
        return auditRepository.findAllByOrderByAtDesc(pageable);
    }

    public Page<AiModelAudit> findByModelCode(String modelCode, Pageable pageable) {
        return auditRepository.findByModelCodeOrderByAtDesc(modelCode, pageable);
    }

    public Page<AiModelAudit> findByActorId(String actorId, Pageable pageable) {
        return auditRepository.findByActorIdOrderByAtDesc(actorId, pageable);
    }

    private String clientIp(HttpServletRequest request) {
        if (request == null) return null;
        String xf = request.getHeader("X-Forwarded-For");
        if (xf != null && !xf.isBlank()) {
            int comma = xf.indexOf(',');
            return (comma > 0 ? xf.substring(0, comma) : xf).trim();
        }
        return request.getRemoteAddr();
    }
}
