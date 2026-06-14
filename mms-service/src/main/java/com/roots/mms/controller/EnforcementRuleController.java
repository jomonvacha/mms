package com.roots.mms.controller;

import com.roots.mms.entity.EnforcementRule;
import com.roots.mms.service.EnforcementRuleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@Slf4j
public class EnforcementRuleController {

    private final EnforcementRuleService enforcementRuleService;

    // ── Public endpoints (no auth — read-only config data) ────────────

    @GetMapping("/api/enforcement-rules/system")
    public ResponseEntity<List<EnforcementRule>> getSystemRules() {
        return ResponseEntity.ok(enforcementRuleService.getSystemRules());
    }

    @GetMapping("/api/enforcement-rules/optional")
    public ResponseEntity<List<EnforcementRule>> getOptionalRules() {
        return ResponseEntity.ok(enforcementRuleService.getOptionalRules());
    }

    // ── Admin endpoints ───────────────────────────────────────────────

    @GetMapping("/api/admin/enforcement-rules")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<EnforcementRule>> getAll() {
        return ResponseEntity.ok(enforcementRuleService.getAll());
    }

    @PostMapping("/api/admin/enforcement-rules")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<EnforcementRule> create(@RequestBody EnforcementRule rule) {
        return ResponseEntity.ok(enforcementRuleService.create(rule));
    }

    @PutMapping("/api/admin/enforcement-rules/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<EnforcementRule> update(@PathVariable String id,
                                                  @RequestBody Map<String, Object> patch) {
        return ResponseEntity.ok(enforcementRuleService.update(id, patch));
    }

    @DeleteMapping("/api/admin/enforcement-rules/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        enforcementRuleService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
