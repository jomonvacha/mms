package com.roots.mms.controller;

import com.roots.mms.entity.SignupInviteCode;
import com.roots.mms.security.SecurityUtils;
import com.roots.mms.service.SignupInviteCodeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Admin-only CRUD for IDFY signup invite codes.
 */
@RestController
@RequestMapping("/api/admin/signup-invites")
@RequiredArgsConstructor
@Slf4j
public class AdminSignupInviteController {

    private final SignupInviteCodeService service;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<SignupInviteCode>> list() {
        return ResponseEntity.ok(service.listAll());
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<SignupInviteCode> create(@RequestBody Map<String, Object> body) {
        String code = (String) body.get("code");
        String description = (String) body.get("description");
        Integer maxUses = body.get("maxUses") == null ? null : ((Number) body.get("maxUses")).intValue();
        String expiresAtStr = (String) body.get("expiresAt");
        Instant expiresAt = (expiresAtStr == null || expiresAtStr.isBlank()) ? null : Instant.parse(expiresAtStr);
        String createdBy = SecurityUtils.getCurrentUserIdOrNull();
        log.info("[AdminInvite] Create code={} maxUses={} expiresAt={} by={}", code, maxUses, expiresAt, createdBy);
        return ResponseEntity.ok(service.create(code, description, maxUses, expiresAt, createdBy));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<SignupInviteCode> update(@PathVariable String id, @RequestBody Map<String, Object> body) {
        Boolean active = (Boolean) body.get("active");
        Integer maxUses = body.get("maxUses") == null ? null : ((Number) body.get("maxUses")).intValue();
        String expiresAtStr = (String) body.get("expiresAt");
        Instant expiresAt = (expiresAtStr == null || expiresAtStr.isBlank()) ? null : Instant.parse(expiresAtStr);
        String description = (String) body.get("description");
        log.info("[AdminInvite] Update id={} active={} maxUses={} expiresAt={}", id, active, maxUses, expiresAt);
        return ResponseEntity.ok(service.update(id, active, maxUses, expiresAt, description));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        log.warn("[AdminInvite] Delete id={} by={}", id, SecurityUtils.getCurrentUserIdOrNull());
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
