package com.roots.mms.controller;

import com.roots.mms.dto.request.UpsertCategoryRequest;
import com.roots.mms.dto.request.UpsertTierEntitlementRequest;
import com.roots.mms.dto.request.UpsertTierRequest;
import com.roots.mms.dto.response.CategoryResponse;
import com.roots.mms.dto.response.EntitlementResponse;
import com.roots.mms.dto.response.MessageResponse;
import com.roots.mms.dto.response.TierResponse;
import com.roots.mms.entity.Entitlement;
import com.roots.mms.security.SecurityUtils;
import com.roots.mms.service.EntitlementService;
import com.roots.mms.service.MembershipGovernanceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Admin/manager-facing governance endpoints: membership categories, tiers,
 * entitlement definitions, and per-tier entitlement values. Member endpoints
 * for reading resolved entitlements live separately in
 * {@code EntitlementsController}.
 */
@RestController
@Slf4j
@RequiredArgsConstructor
@RequestMapping("/api/governance")
@PreAuthorize("hasRole('ADMIN') or hasRole('MANAGER')")
public class MembershipGovernanceController {

    private final MembershipGovernanceService governanceService;
    private final EntitlementService entitlementService;

    // ── Categories ──────────────────────────────────────────────────────────

    /**
     * Public read for any authenticated user — members use this to pick a plan
     * during the self-serve tier selection flow. Mutations remain ADMIN/MANAGER
     * via the class-level {@code @PreAuthorize}.
     */
    @GetMapping("/categories")
    @PreAuthorize("isAuthenticated()")
    public List<CategoryResponse> listCategories() {
        return governanceService.listCategoriesWithTiers();
    }

    @GetMapping("/categories/{code}")
    public CategoryResponse getCategory(@PathVariable String code) {
        return governanceService.getCategory(code);
    }

    @PostMapping("/categories")
    public ResponseEntity<CategoryResponse> createCategory(@Valid @RequestBody UpsertCategoryRequest req) {
        log.info("[Governance] Create category {} by principal={}", req.getCode(), SecurityUtils.getCurrentUserIdOrNull());
        return ResponseEntity.ok(governanceService.createCategory(req));
    }

    @PutMapping("/categories/{code}")
    public CategoryResponse updateCategory(@PathVariable String code,
                                           @Valid @RequestBody UpsertCategoryRequest req) {
        log.info("[Governance] Update category {} by principal={}", code, SecurityUtils.getCurrentUserIdOrNull());
        return governanceService.updateCategory(code, req);
    }

    @DeleteMapping("/categories/{code}")
    public ResponseEntity<MessageResponse> deleteCategory(@PathVariable String code) {
        log.warn("[Governance] Delete category {} by principal={}", code, SecurityUtils.getCurrentUserIdOrNull());
        governanceService.deleteCategory(code);
        return ResponseEntity.ok(new MessageResponse("Category deleted"));
    }

    // ── Tiers ───────────────────────────────────────────────────────────────

    @GetMapping("/categories/{code}/tiers")
    public List<TierResponse> listTiers(@PathVariable String code) {
        return governanceService.listTiers(code);
    }

    @GetMapping("/tiers")
    public List<TierResponse> listAllTiers() {
        return governanceService.listAllTiers();
    }

    @PostMapping("/categories/{code}/tiers")
    public ResponseEntity<TierResponse> createTier(@PathVariable String code,
                                                   @Valid @RequestBody UpsertTierRequest req) {
        log.info("[Governance] Create tier {}/{} by principal={}", code, req.getTierCode(), SecurityUtils.getCurrentUserIdOrNull());
        return ResponseEntity.ok(governanceService.createTier(code, req));
    }

    @PutMapping("/categories/{code}/tiers/{tierCode}")
    public TierResponse updateTier(@PathVariable String code,
                                   @PathVariable String tierCode,
                                   @Valid @RequestBody UpsertTierRequest req) {
        log.info("[Governance] Update tier {}/{} by principal={}", code, tierCode, SecurityUtils.getCurrentUserIdOrNull());
        return governanceService.updateTier(code, tierCode, req);
    }

    @DeleteMapping("/categories/{code}/tiers/{tierCode}")
    public ResponseEntity<MessageResponse> deleteTier(@PathVariable String code,
                                                      @PathVariable String tierCode) {
        log.warn("[Governance] Delete tier {}/{} by principal={}", code, tierCode, SecurityUtils.getCurrentUserIdOrNull());
        governanceService.deleteTier(code, tierCode);
        return ResponseEntity.ok(new MessageResponse("Tier deleted"));
    }

    // ── Entitlement definitions ─────────────────────────────────────────────

    @GetMapping("/entitlements")
    public List<EntitlementResponse> listEntitlements() {
        return entitlementService.listEntitlements();
    }

    @PostMapping("/entitlements")
    public ResponseEntity<EntitlementResponse> createEntitlement(@Valid @RequestBody Entitlement payload) {
        return ResponseEntity.ok(entitlementService.createEntitlement(payload));
    }

    @PutMapping("/entitlements/{key}")
    public EntitlementResponse updateEntitlement(@PathVariable String key,
                                                 @Valid @RequestBody Entitlement payload) {
        payload.setKey(key);
        return entitlementService.upsertEntitlement(payload);
    }

    @DeleteMapping("/entitlements/{key}")
    public ResponseEntity<MessageResponse> deleteEntitlement(@PathVariable String key) {
        entitlementService.deleteEntitlement(key);
        return ResponseEntity.ok(new MessageResponse("Entitlement deleted"));
    }

    // ── Tier entitlement values ─────────────────────────────────────────────

    @GetMapping("/categories/{code}/tiers/{tierCode}/entitlements")
    public Map<String, String> getTierEntitlements(@PathVariable String code,
                                                   @PathVariable String tierCode) {
        return entitlementService.getTierValues(code, tierCode);
    }

    @PutMapping("/categories/{code}/tiers/{tierCode}/entitlements")
    public ResponseEntity<MessageResponse> setTierEntitlement(@PathVariable String code,
                                                              @PathVariable String tierCode,
                                                              @Valid @RequestBody UpsertTierEntitlementRequest req) {
        entitlementService.setTierEntitlement(code, tierCode, req);
        return ResponseEntity.ok(new MessageResponse("Tier entitlement updated"));
    }

    @DeleteMapping("/categories/{code}/tiers/{tierCode}/entitlements/{entitlementKey}")
    public ResponseEntity<MessageResponse> removeTierEntitlement(@PathVariable String code,
                                                                 @PathVariable String tierCode,
                                                                 @PathVariable String entitlementKey) {
        entitlementService.removeTierEntitlement(code, tierCode, entitlementKey);
        return ResponseEntity.ok(new MessageResponse("Tier entitlement removed"));
    }
}
