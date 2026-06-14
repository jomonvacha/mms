package com.roots.mms.service;

import com.roots.mms.dto.request.UpsertTierEntitlementRequest;
import com.roots.mms.dto.response.EntitlementResponse;
import com.roots.mms.dto.response.ResolvedEntitlementsResponse;
import com.roots.mms.entity.Entitlement;
import com.roots.mms.entity.Member;
import com.roots.mms.entity.MembershipCategoryConfig;
import com.roots.mms.entity.MembershipTierConfig;
import com.roots.mms.entity.TierEntitlement;
import com.roots.mms.exception.BusinessRuleException;
import com.roots.mms.exception.DuplicateResourceException;
import com.roots.mms.exception.ResourceNotFoundException;
import com.roots.mms.exception.ValidationException;
import com.roots.mms.repository.EntitlementRepository;
import com.roots.mms.repository.MemberRepository;
import com.roots.mms.repository.MembershipCategoryConfigRepository;
import com.roots.mms.repository.MembershipTierConfigRepository;
import com.roots.mms.repository.TierEntitlementRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Single entry point for anything touching entitlements: definitions,
 * per-tier values, and resolution for an individual member. Features should
 * ask this service rather than making tier decisions inline, so all gating
 * logic stays consistent.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class EntitlementService {

    private final EntitlementRepository entitlementRepo;
    private final TierEntitlementRepository tierEntitlementRepo;
    private final MembershipCategoryConfigRepository categoryRepo;
    private final MembershipTierConfigRepository tierRepo;
    private final MemberRepository memberRepository;

    // ── Definitions (admin) ─────────────────────────────────────────────────

    public List<EntitlementResponse> listEntitlements() {
        return entitlementRepo.findAll().stream()
                .sorted(Comparator.comparing(Entitlement::getKey))
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public EntitlementResponse upsertEntitlement(Entitlement incoming) {
        if (incoming.getKey() == null || incoming.getKey().isBlank()) {
            throw new ValidationException("Entitlement key is required");
        }
        var existing = entitlementRepo.findByKey(incoming.getKey());
        if (existing.isPresent()) {
            var e = existing.get();
            if (incoming.getDisplayName() != null) e.setDisplayName(incoming.getDisplayName());
            if (incoming.getDescription() != null) e.setDescription(incoming.getDescription());
            if (incoming.getValueType() != null) e.setValueType(incoming.getValueType());
            if (incoming.getCategory() != null) e.setCategory(incoming.getCategory());
            if (incoming.getDefaultValue() != null) e.setDefaultValue(incoming.getDefaultValue());
            return toResponse(entitlementRepo.save(e));
        }
        incoming.setSystem(false);
        return toResponse(entitlementRepo.save(incoming));
    }

    @Transactional
    public void deleteEntitlement(String key) {
        var entitlement = entitlementRepo.findByKey(key)
                .orElseThrow(() -> new ResourceNotFoundException("Entitlement", "key", key));
        if (Boolean.TRUE.equals(entitlement.getSystem())) {
            throw new BusinessRuleException("System entitlements cannot be deleted");
        }
        tierEntitlementRepo.deleteByEntitlementKey(key);
        entitlementRepo.delete(entitlement);
    }

    // ── Tier ↔ Entitlement bindings (admin) ─────────────────────────────────

    public Map<String, String> getTierValues(String categoryCode, String tierCode) {
        tierRepo.findByCategoryCodeAndTierCode(categoryCode, tierCode)
                .orElseThrow(() -> new ResourceNotFoundException("MembershipTier",
                        "categoryCode+tierCode", categoryCode + "/" + tierCode));
        Map<String, String> values = new LinkedHashMap<>();
        for (TierEntitlement te : tierEntitlementRepo.findByCategoryCodeAndTierCode(categoryCode, tierCode)) {
            values.put(te.getEntitlementKey(), te.getValue());
        }
        return values;
    }

    @Transactional
    public void setTierEntitlement(String categoryCode, String tierCode, UpsertTierEntitlementRequest req) {
        tierRepo.findByCategoryCodeAndTierCode(categoryCode, tierCode)
                .orElseThrow(() -> new ResourceNotFoundException("MembershipTier",
                        "categoryCode+tierCode", categoryCode + "/" + tierCode));
        Entitlement def = entitlementRepo.findByKey(req.getEntitlementKey())
                .orElseThrow(() -> new ResourceNotFoundException("Entitlement", "key", req.getEntitlementKey()));
        validateValueForType(def, req.getValue());

        var existing = tierEntitlementRepo.findByCategoryCodeAndTierCodeAndEntitlementKey(
                categoryCode, tierCode, req.getEntitlementKey());
        TierEntitlement te;
        if (existing.isPresent()) {
            te = existing.get();
            te.setValue(req.getValue());
        } else {
            te = TierEntitlement.builder()
                    .categoryCode(categoryCode)
                    .tierCode(tierCode)
                    .entitlementKey(req.getEntitlementKey())
                    .value(req.getValue())
                    .build();
        }
        tierEntitlementRepo.save(te);
        log.info("[Entitlements] Set {}={} for {}/{}", req.getEntitlementKey(), req.getValue(), categoryCode, tierCode);
    }

    @Transactional
    public void removeTierEntitlement(String categoryCode, String tierCode, String entitlementKey) {
        var existing = tierEntitlementRepo.findByCategoryCodeAndTierCodeAndEntitlementKey(
                categoryCode, tierCode, entitlementKey);
        if (existing.isEmpty()) {
            throw new ResourceNotFoundException("TierEntitlement",
                    "key", categoryCode + "/" + tierCode + "/" + entitlementKey);
        }
        tierEntitlementRepo.delete(existing.get());
    }

    // ── Resolution (any authenticated caller) ───────────────────────────────

    /**
     * Resolve the flat set of entitlements that apply to a specific member.
     * If the member has no category/tier assigned yet, the response falls
     * back to entitlement default values so callers can still make a safe
     * decision.
     */
    public ResolvedEntitlementsResponse resolveForMember(String userId) {
        Member member = memberRepository.findByUserId(java.util.UUID.fromString(userId)).orElse(null);

        String categoryCode = member != null ? member.getCategoryCode() : null;
        String tierCode = member != null ? member.getTierCode() : null;
        Boolean categoryEnabled = null;
        Boolean tierEnabled = null;

        // Start with defaults; tier-specific values override if present.
        Map<String, String> resolved = new LinkedHashMap<>();
        for (Entitlement e : entitlementRepo.findAll()) {
            resolved.put(e.getKey(), e.getDefaultValue());
        }

        if (categoryCode != null && tierCode != null) {
            MembershipCategoryConfig cat = categoryRepo.findByCode(categoryCode).orElse(null);
            MembershipTierConfig tier = tierRepo.findByCategoryCodeAndTierCode(categoryCode, tierCode).orElse(null);
            categoryEnabled = cat != null && Boolean.TRUE.equals(cat.getEnabled());
            tierEnabled = tier != null && Boolean.TRUE.equals(tier.getEnabled());

            // Only layer tier-specific overrides when both category and tier are enabled.
            // A disabled category or tier should degrade to defaults (typically deny).
            if (Boolean.TRUE.equals(categoryEnabled) && Boolean.TRUE.equals(tierEnabled)) {
                for (TierEntitlement te : tierEntitlementRepo.findByCategoryCodeAndTierCode(categoryCode, tierCode)) {
                    resolved.put(te.getEntitlementKey(), te.getValue());
                }
            }
        }

        return ResolvedEntitlementsResponse.builder()
                .userId(userId)
                .categoryCode(categoryCode)
                .tierCode(tierCode)
                .categoryEnabled(categoryEnabled)
                .tierEnabled(tierEnabled)
                .entitlements(resolved)
                .build();
    }

    // ── Parsing helpers for consumers ───────────────────────────────────────

    public static boolean asBoolean(String value) {
        return value != null && ("true".equalsIgnoreCase(value.trim()) || "1".equals(value.trim()));
    }

    public static int asInt(String value, int fallback) {
        if (value == null || value.isBlank()) return fallback;
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException nfe) {
            return fallback;
        }
    }

    // ── Internals ───────────────────────────────────────────────────────────

    private void validateValueForType(Entitlement def, String value) {
        if (value == null) throw new ValidationException("Value is required");
        switch (def.getValueType()) {
            case BOOLEAN -> {
                String v = value.trim().toLowerCase();
                if (!v.equals("true") && !v.equals("false")) {
                    throw new ValidationException("BOOLEAN entitlement value must be 'true' or 'false'");
                }
            }
            case INTEGER -> {
                try {
                    Integer.parseInt(value.trim());
                } catch (NumberFormatException nfe) {
                    throw new ValidationException("INTEGER entitlement value must be a whole number");
                }
            }
            case STRING -> { /* no extra constraint */ }
        }
    }

    private EntitlementResponse toResponse(Entitlement e) {
        return EntitlementResponse.builder()
                .id(e.getId() != null ? e.getId().toString() : null)
                .key(e.getKey())
                .displayName(e.getDisplayName())
                .description(e.getDescription())
                .valueType(e.getValueType())
                .category(e.getCategory())
                .defaultValue(e.getDefaultValue())
                .system(e.getSystem())
                .build();
    }

    /** For tests and governance flows that have already looked up the duplicate key. */
    public Entitlement requireByKey(String key) {
        return entitlementRepo.findByKey(key)
                .orElseThrow(() -> new ResourceNotFoundException("Entitlement", "key", key));
    }

    @Transactional
    public EntitlementResponse createEntitlement(Entitlement e) {
        if (entitlementRepo.existsByKey(e.getKey())) {
            throw new DuplicateResourceException("Entitlement", "key", e.getKey());
        }
        return upsertEntitlement(e);
    }
}
