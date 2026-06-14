package com.roots.mms.controller;

import com.roots.mms.dto.request.CreateAiModelRequest;
import com.roots.mms.dto.request.UpdateAiModelRequest;
import com.roots.mms.entity.AiModel;
import com.roots.mms.entity.AiModelAudit;
import com.roots.mms.entity.AiModelProvider;
import com.roots.mms.entity.AiModelTierBinding;
import com.roots.mms.security.SecurityUtils;
import com.roots.mms.service.AiModelAuditService;
import com.roots.mms.service.AiModelService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Admin governance surface for the AI model registry. ADMIN is required for
 * every mutation; reads are available to ADMIN and MANAGER so managers can
 * audit the matrix without risk of changing it.
 *
 * <p>Every successful mutation emits an {@link AiModelAudit} record via
 * {@link AiModelAuditService}. Audit writes don't abort a successful write
 * if they fail; they log and move on, so a downstream Mongo hiccup on the
 * audit collection never breaks governance.
 */
@RestController
@RequestMapping("/api/admin/models")
@RequiredArgsConstructor
@Slf4j
public class AdminModelsController {

    private final AiModelService service;
    private final AiModelAuditService auditService;

    // ── Providers ───────────────────────────────────────────────────

    @GetMapping("/providers")
    @PreAuthorize("hasRole('ADMIN') or hasRole('MANAGER')")
    public ResponseEntity<List<AiModelProvider>> listProviders() {
        return ResponseEntity.ok(service.listProviders());
    }

    @PutMapping("/providers/{code}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AiModelProvider> upsertProvider(@PathVariable String code,
                                                           @RequestBody Map<String, Object> body,
                                                           HttpServletRequest httpReq) {
        String displayName = (String) body.getOrDefault("displayName", code);
        String baseUrl = (String) body.get("baseUrl");
        Boolean enabled = (Boolean) body.getOrDefault("enabled", Boolean.TRUE);
        String actor = SecurityUtils.getCurrentUserIdOrNull();
        log.info("[AiModels] Upsert provider {} by actor={}", code, actor);

        var beforeJson = service.listProviders().stream()
                .filter(p -> code.equals(p.getCode()))
                .findFirst()
                .map(auditService::serialize)
                .orElse(null);
        AiModelProvider after = service.upsertProvider(code, displayName, baseUrl, enabled);
        var action = beforeJson == null
                ? AiModelAudit.Action.PROVIDER_CREATED
                : AiModelAudit.Action.PROVIDER_UPDATED;
        auditService.record(action, null, null, beforeJson, after, httpReq);
        return ResponseEntity.ok(after);
    }

    // ── Models ──────────────────────────────────────────────────────

    @GetMapping
    @PreAuthorize("hasRole('ADMIN') or hasRole('MANAGER')")
    public ResponseEntity<List<AiModel>> list() {
        return ResponseEntity.ok(service.listModels());
    }

    @GetMapping("/{code}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('MANAGER')")
    public ResponseEntity<AiModel> getOne(@PathVariable String code) {
        return service.findModel(code)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AiModel> create(@Valid @RequestBody CreateAiModelRequest req,
                                          HttpServletRequest httpReq) {
        String actor = SecurityUtils.getCurrentUserIdOrNull();
        log.info("[AiModels] Create model {} by actor={}", req.getCode(), actor);
        AiModel entity = AiModel.builder()
                .code(req.getCode())
                .providerCode(req.getProviderCode())
                .displayName(req.getDisplayName())
                .description(req.getDescription())
                .qualityLevel(req.getQualityLevel())
                .costLevel(req.getCostLevel())
                .sortOrder(req.getSortOrder())
                .lockStyle(req.getLockStyle())
                .replacesModelCode(req.getReplacesModelCode())
                .build();
        AiModel after = service.createModel(entity, actor);
        auditService.record(AiModelAudit.Action.MODEL_CREATED, after.getCode(), null, null, after, httpReq);
        return ResponseEntity.ok(after);
    }

    @PutMapping("/{code}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AiModel> update(@PathVariable String code,
                                          @Valid @RequestBody UpdateAiModelRequest req,
                                          HttpServletRequest httpReq) {
        String actor = SecurityUtils.getCurrentUserIdOrNull();
        log.info("[AiModels] Update model {} by actor={}", code, actor);

        // Snapshot BEFORE state as JSON so the service's in-place mutation
        // can't pollute the pre-change record.
        String beforeJson = service.findModel(code).map(auditService::serialize).orElse(null);

        AiModel patch = AiModel.builder()
                .providerCode(req.getProviderCode())
                .displayName(req.getDisplayName())
                .description(req.getDescription())
                .qualityLevel(req.getQualityLevel())
                .costLevel(req.getCostLevel())
                .sortOrder(req.getSortOrder())
                .lockStyle(req.getLockStyle())
                .replacesModelCode(req.getReplacesModelCode())
                .build();
        AiModel after = service.updateModel(code, patch, actor);
        auditService.record(AiModelAudit.Action.MODEL_UPDATED, code, null, beforeJson, after, httpReq);
        return ResponseEntity.ok(after);
    }

    @PatchMapping("/{code}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AiModel> setStatus(@PathVariable String code,
                                             @RequestBody Map<String, String> body,
                                             HttpServletRequest httpReq) {
        String statusStr = body.get("status");
        if (statusStr == null) return ResponseEntity.badRequest().build();
        AiModel.Status status;
        try {
            status = AiModel.Status.valueOf(statusStr.toUpperCase());
        } catch (IllegalArgumentException iae) {
            return ResponseEntity.badRequest().build();
        }
        String actor = SecurityUtils.getCurrentUserIdOrNull();
        log.info("[AiModels] Set status {} → {} by actor={}", code, status, actor);

        String beforeJson = service.findModel(code).map(auditService::serialize).orElse(null);
        AiModel after = service.setStatus(code, status, actor);
        auditService.record(AiModelAudit.Action.MODEL_STATUS_CHANGED, code, null, beforeJson, after, httpReq);
        return ResponseEntity.ok(after);
    }

    @DeleteMapping("/{code}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable String code, HttpServletRequest httpReq) {
        String actor = SecurityUtils.getCurrentUserIdOrNull();
        log.warn("[AiModels] Delete model {} by actor={}", code, actor);
        String beforeJson = service.findModel(code).map(auditService::serialize).orElse(null);
        service.deleteModel(code);
        auditService.record(AiModelAudit.Action.MODEL_DELETED, code, null, beforeJson, null, httpReq);
        return ResponseEntity.noContent().build();
    }

    // ── Bindings ────────────────────────────────────────────────────

    @GetMapping("/{code}/bindings")
    @PreAuthorize("hasRole('ADMIN') or hasRole('MANAGER')")
    public ResponseEntity<List<AiModelTierBinding>> listBindings(@PathVariable String code) {
        return ResponseEntity.ok(service.listBindings(code));
    }

    @PutMapping("/{code}/bindings/{category}/{tier}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AiModelTierBinding> setBinding(@PathVariable String code,
                                                          @PathVariable String category,
                                                          @PathVariable String tier,
                                                          @RequestBody(required = false) Map<String, Object> body,
                                                          HttpServletRequest httpReq) {
        Boolean isDefault = body != null ? (Boolean) body.get("isDefault") : null;
        String labelOverride = body != null ? (String) body.get("labelOverride") : null;
        String actor = SecurityUtils.getCurrentUserIdOrNull();
        log.info("[AiModels] Bind {} ↔ {}/{} isDefault={} by actor={}", code, category, tier, isDefault, actor);

        // Snapshot before-state as JSON so we can tell create vs update in audit.
        var existing = service.listBindings(code).stream()
                .filter(b -> category.equals(b.getCategoryCode()) && tier.equals(b.getTierCode()))
                .findFirst();
        String beforeJson = existing.map(auditService::serialize).orElse(null);

        AiModelTierBinding after = service.setBinding(code, category, tier, isDefault, labelOverride, actor);
        var action = beforeJson == null
                ? AiModelAudit.Action.BINDING_CREATED
                : AiModelAudit.Action.BINDING_UPDATED;
        auditService.record(action, code,
                new AiModelAudit.BindingRef(category, tier),
                beforeJson, after, httpReq);
        return ResponseEntity.ok(after);
    }

    @DeleteMapping("/{code}/bindings/{category}/{tier}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> removeBinding(@PathVariable String code,
                                               @PathVariable String category,
                                               @PathVariable String tier,
                                               HttpServletRequest httpReq) {
        String actor = SecurityUtils.getCurrentUserIdOrNull();
        log.info("[AiModels] Unbind {} ↔ {}/{} by actor={}", code, category, tier, actor);

        var existing = service.listBindings(code).stream()
                .filter(b -> category.equals(b.getCategoryCode()) && tier.equals(b.getTierCode()))
                .findFirst();
        String beforeJson = existing.map(auditService::serialize).orElse(null);

        service.removeBinding(code, category, tier);
        auditService.record(AiModelAudit.Action.BINDING_DELETED, code,
                new AiModelAudit.BindingRef(category, tier),
                beforeJson, null, httpReq);
        return ResponseEntity.noContent().build();
    }

    // ── Audit read surface ──────────────────────────────────────────

    /**
     * Paginated audit feed. Takes optional {@code modelCode} and {@code actorId}
     * filters; otherwise returns the most recent events across the registry.
     */
    @GetMapping("/audit")
    @PreAuthorize("hasRole('ADMIN') or hasRole('MANAGER')")
    public ResponseEntity<org.springframework.data.domain.Page<AiModelAudit>> audit(
            @RequestParam(required = false) String modelCode,
            @RequestParam(required = false) String actorId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        var pageable = org.springframework.data.domain.PageRequest.of(
                Math.max(0, page), Math.min(200, Math.max(1, size)));
        if (modelCode != null && !modelCode.isBlank()) {
            return ResponseEntity.ok(auditService.findByModelCode(modelCode, pageable));
        }
        if (actorId != null && !actorId.isBlank()) {
            return ResponseEntity.ok(auditService.findByActorId(actorId, pageable));
        }
        return ResponseEntity.ok(auditService.findAll(pageable));
    }
}
