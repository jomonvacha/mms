package com.roots.mms.service;

import com.roots.mms.entity.AiModel;
import com.roots.mms.entity.AiModelProvider;
import com.roots.mms.entity.AiModelTierBinding;
import com.roots.mms.entity.Member;
import com.roots.mms.repository.AiModelProviderRepository;
import com.roots.mms.repository.AiModelRepository;
import com.roots.mms.repository.AiModelTierBindingRepository;
import com.roots.mms.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Business logic for the AI model registry + tier bindings.
 *
 * <p>Keeps controller surfaces thin: admin writes run through {@link #createModel},
 * {@link #updateModel}, {@link #setBinding}, etc., and the resolver surface runs
 * through {@link #resolveForMember} which yields everything a member-facing UI
 * needs without leaking admin-only fields.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AiModelService {

    private final AiModelProviderRepository providerRepository;
    private final AiModelRepository modelRepository;
    private final AiModelTierBindingRepository bindingRepository;
    private final MemberRepository memberRepository;

    // ── Provider CRUD ───────────────────────────────────────────────

    public List<AiModelProvider> listProviders() {
        return providerRepository.findAll();
    }

    public AiModelProvider upsertProvider(String code, String displayName, String baseUrl, Boolean enabled) {
        AiModelProvider p = providerRepository.findByCode(code)
                .orElseGet(() -> AiModelProvider.builder().code(code).build());
        p.setDisplayName(displayName);
        p.setBaseUrl(baseUrl);
        p.setEnabled(enabled == null ? Boolean.TRUE : enabled);
        return providerRepository.save(p);
    }

    // ── Model CRUD ──────────────────────────────────────────────────

    public List<AiModel> listModels() {
        return modelRepository.findAllByOrderBySortOrderAscDisplayNameAsc();
    }

    public Optional<AiModel> findModel(String code) {
        return modelRepository.findByCode(code);
    }

    /**
     * Create a new model row. Caller must enforce authorization — this is
     * service-layer storage only.
     */
    public AiModel createModel(AiModel patch, String actorId) {
        if (patch.getCode() == null || patch.getCode().isBlank()) {
            throw new IllegalArgumentException("Model code is required.");
        }
        if (modelRepository.existsByCode(patch.getCode())) {
            throw new IllegalStateException("Model code already exists: " + patch.getCode());
        }
        if (patch.getProviderCode() == null || providerRepository.findByCode(patch.getProviderCode()).isEmpty()) {
            throw new IllegalArgumentException("Unknown provider: " + patch.getProviderCode());
        }
        patch.setId(null);
        patch.setStatus(patch.getStatus() == null ? AiModel.Status.ENABLED : patch.getStatus());
        patch.setLockStyle(patch.getLockStyle() == null ? AiModel.LockStyle.LOCK : patch.getLockStyle());
        patch.setCreatedBy(actorId);
        patch.setUpdatedBy(actorId);
        return modelRepository.save(patch);
    }

    public AiModel updateModel(String code, AiModel patch, String actorId) {
        AiModel existing = modelRepository.findByCode(code)
                .orElseThrow(() -> new IllegalArgumentException("Model not found: " + code));

        if (patch.getProviderCode() != null && !patch.getProviderCode().equals(existing.getProviderCode())) {
            if (providerRepository.findByCode(patch.getProviderCode()).isEmpty()) {
                throw new IllegalArgumentException("Unknown provider: " + patch.getProviderCode());
            }
            existing.setProviderCode(patch.getProviderCode());
        }
        if (patch.getDisplayName() != null) existing.setDisplayName(patch.getDisplayName());
        if (patch.getDescription() != null) existing.setDescription(patch.getDescription());
        if (patch.getQualityLevel() != null) existing.setQualityLevel(patch.getQualityLevel());
        if (patch.getCostLevel() != null) existing.setCostLevel(patch.getCostLevel());
        if (patch.getSortOrder() != null) existing.setSortOrder(patch.getSortOrder());
        if (patch.getLockStyle() != null) existing.setLockStyle(patch.getLockStyle());
        if (patch.getReplacesModelCode() != null) existing.setReplacesModelCode(patch.getReplacesModelCode());

        existing.setUpdatedBy(actorId);
        return modelRepository.save(existing);
    }

    public AiModel setStatus(String code, AiModel.Status status, String actorId) {
        AiModel m = modelRepository.findByCode(code)
                .orElseThrow(() -> new IllegalArgumentException("Model not found: " + code));
        m.setStatus(status);
        m.setDeprecatedAt(status == AiModel.Status.DEPRECATED ? Instant.now() : null);
        m.setUpdatedBy(actorId);
        return modelRepository.save(m);
    }

    public void deleteModel(String code) {
        AiModel existing = modelRepository.findByCode(code)
                .orElseThrow(() -> new IllegalArgumentException("Model not found: " + code));
        bindingRepository.deleteByModelCode(code);
        modelRepository.delete(existing);
    }

    // ── Bindings ────────────────────────────────────────────────────

    public List<AiModelTierBinding> listBindings(String modelCode) {
        return bindingRepository.findByModelCode(modelCode);
    }

    /**
     * Create or update a (model, category, tier) binding. When {@code isDefault}
     * is true, all other bindings within the same (category, tier) have their
     * default flag cleared so "exactly one default" holds.
     */
    public AiModelTierBinding setBinding(String modelCode, String categoryCode, String tierCode,
                                          Boolean isDefault, String labelOverride, String actorId) {
        if (modelRepository.findByCode(modelCode).isEmpty()) {
            throw new IllegalArgumentException("Model not found: " + modelCode);
        }

        AiModelTierBinding binding = bindingRepository
                .findByModelCodeAndCategoryCodeAndTierCode(modelCode, categoryCode, tierCode)
                .orElseGet(() -> AiModelTierBinding.builder()
                        .modelCode(modelCode)
                        .categoryCode(categoryCode)
                        .tierCode(tierCode)
                        .createdBy(actorId)
                        .build());

        if (Boolean.TRUE.equals(isDefault)) {
            // Clear the default flag on any sibling binding first.
            for (AiModelTierBinding other : bindingRepository
                    .findByCategoryCodeAndTierCodeAndIsDefault(categoryCode, tierCode, true)) {
                if (!other.getModelCode().equals(modelCode)) {
                    other.setIsDefault(false);
                    other.setUpdatedBy(actorId);
                    bindingRepository.save(other);
                }
            }
            binding.setIsDefault(true);
        } else if (isDefault != null) {
            binding.setIsDefault(false);
        }

        if (labelOverride != null) binding.setLabelOverride(labelOverride.isBlank() ? null : labelOverride);
        binding.setUpdatedBy(actorId);
        return bindingRepository.save(binding);
    }

    public void removeBinding(String modelCode, String categoryCode, String tierCode) {
        bindingRepository.findByModelCodeAndCategoryCodeAndTierCode(modelCode, categoryCode, tierCode)
                .ifPresent(bindingRepository::delete);
    }

    // ── Resolver ────────────────────────────────────────────────────

    /**
     * Compact member-facing view of a single model, with the included / locked
     * state resolved for the caller's tier.
     */
    public record ResolvedModelView(
            String code,
            String displayName,
            String description,
            String provider,
            Integer qualityLevel,
            Integer costLevel,
            Integer sortOrder,
            String status,
            boolean included,
            boolean isDefault,
            boolean locked,
            String lockReason,
            String labelOverride
    ) {}

    public record ResolvedCatalog(
            String categoryCode,
            String tierCode,
            String defaultModelCode,
            List<ResolvedModelView> models
    ) {}

    /**
     * Resolve the visible catalog for a given member. DEPRECATED and DISABLED
     * models are excluded entirely — existing personas that still reference
     * them fall through to a runtime fallback (phase 3) rather than being
     * re-offered in the UI.
     */
    public ResolvedCatalog resolveForMember(String userId) {
        Member member = memberRepository.findByUserId(java.util.UUID.fromString(userId)).orElse(null);
        String categoryCode = member != null ? member.getCategoryCode() : null;
        String tierCode = member != null ? member.getTierCode() : null;

        List<AiModel> allModels = modelRepository.findAllByOrderBySortOrderAscDisplayNameAsc();
        List<AiModelTierBinding> tierBindings = (categoryCode != null && tierCode != null)
                ? bindingRepository.findByCategoryCodeAndTierCode(categoryCode, tierCode)
                : Collections.emptyList();

        Map<String, AiModelTierBinding> byModelCode = new java.util.HashMap<>();
        for (AiModelTierBinding b : tierBindings) byModelCode.put(b.getModelCode(), b);

        String defaultModelCode = tierBindings.stream()
                .filter(b -> Boolean.TRUE.equals(b.getIsDefault()))
                .map(AiModelTierBinding::getModelCode)
                .findFirst()
                .orElse(null);

        List<ResolvedModelView> views = new ArrayList<>();
        for (AiModel m : allModels) {
            if (m.getStatus() != AiModel.Status.ENABLED) continue;

            AiModelTierBinding b = byModelCode.get(m.getCode());
            boolean included = b != null;
            AiModel.LockStyle style = m.getLockStyle() != null ? m.getLockStyle() : AiModel.LockStyle.LOCK;

            // HIDE + not included → drop entirely.
            if (!included && style == AiModel.LockStyle.HIDE) continue;

            views.add(new ResolvedModelView(
                    m.getCode(),
                    m.getDisplayName(),
                    m.getDescription(),
                    m.getProviderCode(),
                    m.getQualityLevel(),
                    m.getCostLevel(),
                    m.getSortOrder(),
                    m.getStatus().name(),
                    included,
                    included && Boolean.TRUE.equals(b.getIsDefault()),
                    !included,
                    included ? null : upgradeHint(m),
                    included ? b.getLabelOverride() : null
            ));
        }

        views.sort(Comparator.comparingInt(v -> v.sortOrder() == null ? 100 : v.sortOrder()));
        return new ResolvedCatalog(categoryCode, tierCode, defaultModelCode, views);
    }

    /**
     * Short human-readable hint derived from quality level — useful while the
     * UI evolves beyond binary ADVANCED/PREMIUM chips. The resolver output
     * lets the frontend decide how to phrase the upgrade CTA.
     */
    private String upgradeHint(AiModel m) {
        Integer q = m.getQualityLevel();
        if (q == null) return "PAID";
        if (q >= 4) return "PREMIUM";
        if (q >= 3) return "ADVANCED";
        return "PAID";
    }
}
