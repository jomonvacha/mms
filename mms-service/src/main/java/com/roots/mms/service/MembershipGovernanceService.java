package com.roots.mms.service;

import com.roots.mms.dto.request.UpsertCategoryRequest;
import com.roots.mms.dto.request.UpsertTierRequest;
import com.roots.mms.dto.response.CategoryResponse;
import com.roots.mms.dto.response.TierResponse;
import com.roots.mms.entity.MembershipCategoryConfig;
import com.roots.mms.entity.MembershipTierConfig;
import com.roots.mms.exception.BusinessRuleException;
import com.roots.mms.exception.DuplicateResourceException;
import com.roots.mms.exception.ResourceNotFoundException;
import com.roots.mms.exception.ValidationException;
import com.roots.mms.repository.MembershipCategoryConfigRepository;
import com.roots.mms.repository.MembershipTierConfigRepository;
import com.roots.mms.repository.TierEntitlementRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

/**
 * Admin/manager-facing governance of membership categories and their
 * category-scoped tiers. Lifecycle operations (enable, disable, rename) go
 * through here so logging, validation, and audit hooks stay in one place.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class MembershipGovernanceService {

    private final MembershipCategoryConfigRepository categoryRepo;
    private final MembershipTierConfigRepository tierRepo;
    private final TierEntitlementRepository tierEntitlementRepo;

    // ── Category operations ─────────────────────────────────────────────────

    public List<CategoryResponse> listCategoriesWithTiers() {
        List<MembershipCategoryConfig> categories = categoryRepo.findAllByOrderBySortOrderAscCodeAsc();
        return categories.stream().map(cat -> {
            List<TierResponse> tiers = tierRepo
                    .findByCategoryCodeOrderBySortOrderAscTierCodeAsc(cat.getCode())
                    .stream().map(this::toTierResponse).toList();
            return toCategoryResponse(cat, tiers);
        }).toList();
    }

    public CategoryResponse getCategory(String code) {
        MembershipCategoryConfig cat = categoryRepo.findByCode(code)
                .orElseThrow(() -> new ResourceNotFoundException("MembershipCategory", "code", code));
        List<TierResponse> tiers = tierRepo
                .findByCategoryCodeOrderBySortOrderAscTierCodeAsc(code)
                .stream().map(this::toTierResponse).toList();
        return toCategoryResponse(cat, tiers);
    }

    @Transactional
    public CategoryResponse createCategory(UpsertCategoryRequest req) {
        if (req.getCode() == null || req.getCode().isBlank()) {
            throw new ValidationException("Category code is required");
        }
        if (categoryRepo.existsByCode(req.getCode())) {
            throw new DuplicateResourceException("MembershipCategory", "code", req.getCode());
        }
        MembershipCategoryConfig cat = MembershipCategoryConfig.builder()
                .code(req.getCode())
                .displayName(req.getDisplayName() != null ? req.getDisplayName() : req.getCode())
                .description(req.getDescription())
                .enabled(req.getEnabled() != null ? req.getEnabled() : true)
                .sortOrder(req.getSortOrder() != null ? req.getSortOrder() : 100)
                .system(false)
                .build();
        cat = categoryRepo.save(cat);
        log.info("[Governance] Category created: {}", cat.getCode());
        return toCategoryResponse(cat, List.of());
    }

    @Transactional
    public CategoryResponse updateCategory(String code, UpsertCategoryRequest req) {
        MembershipCategoryConfig cat = categoryRepo.findByCode(code)
                .orElseThrow(() -> new ResourceNotFoundException("MembershipCategory", "code", code));
        if (req.getDisplayName() != null) cat.setDisplayName(req.getDisplayName());
        if (req.getDescription() != null) cat.setDescription(req.getDescription());
        if (req.getEnabled() != null) cat.setEnabled(req.getEnabled());
        if (req.getSortOrder() != null) cat.setSortOrder(req.getSortOrder());
        cat = categoryRepo.save(cat);
        log.info("[Governance] Category updated: {} enabled={}", cat.getCode(), cat.getEnabled());
        List<TierResponse> tiers = tierRepo
                .findByCategoryCodeOrderBySortOrderAscTierCodeAsc(code)
                .stream().map(this::toTierResponse).toList();
        return toCategoryResponse(cat, tiers);
    }

    @Transactional
    public void deleteCategory(String code) {
        MembershipCategoryConfig cat = categoryRepo.findByCode(code)
                .orElseThrow(() -> new ResourceNotFoundException("MembershipCategory", "code", code));
        if (Boolean.TRUE.equals(cat.getSystem())) {
            throw new BusinessRuleException("System categories cannot be deleted. Disable instead.");
        }
        if (tierRepo.countByCategoryCode(code) > 0) {
            throw new BusinessRuleException("Remove tiers before deleting this category.");
        }
        categoryRepo.delete(cat);
        log.warn("[Governance] Category deleted: {}", code);
    }

    // ── Tier operations ─────────────────────────────────────────────────────

    public List<TierResponse> listTiers(String categoryCode) {
        categoryRepo.findByCode(categoryCode)
                .orElseThrow(() -> new ResourceNotFoundException("MembershipCategory", "code", categoryCode));
        return tierRepo.findByCategoryCodeOrderBySortOrderAscTierCodeAsc(categoryCode).stream()
                .map(this::toTierResponse).toList();
    }

    public List<TierResponse> listAllTiers() {
        return tierRepo.findAllByOrderByCategoryCodeAscSortOrderAscTierCodeAsc().stream()
                .map(this::toTierResponse)
                .sorted(Comparator.comparing(TierResponse::getCategoryCode)
                        .thenComparing(TierResponse::getSortOrder)
                        .thenComparing(TierResponse::getTierCode))
                .toList();
    }

    @Transactional
    public TierResponse createTier(String categoryCode, UpsertTierRequest req) {
        categoryRepo.findByCode(categoryCode)
                .orElseThrow(() -> new ResourceNotFoundException("MembershipCategory", "code", categoryCode));
        if (req.getTierCode() == null || req.getTierCode().isBlank()) {
            throw new ValidationException("Tier code is required");
        }
        if (tierRepo.existsByCategoryCodeAndTierCode(categoryCode, req.getTierCode())) {
            throw new DuplicateResourceException("MembershipTier",
                    "categoryCode+tierCode", categoryCode + "/" + req.getTierCode());
        }
        MembershipTierConfig tier = MembershipTierConfig.builder()
                .categoryCode(categoryCode)
                .tierCode(req.getTierCode())
                .displayName(req.getDisplayName() != null ? req.getDisplayName() : req.getTierCode())
                .description(req.getDescription())
                .enabled(req.getEnabled() != null ? req.getEnabled() : true)
                .sortOrder(req.getSortOrder() != null ? req.getSortOrder() : 100)
                .system(false)
                .build();
        tier = tierRepo.save(tier);
        log.info("[Governance] Tier created: {}/{}", categoryCode, tier.getTierCode());
        return toTierResponse(tier);
    }

    @Transactional
    public TierResponse updateTier(String categoryCode, String tierCode, UpsertTierRequest req) {
        MembershipTierConfig tier = tierRepo.findByCategoryCodeAndTierCode(categoryCode, tierCode)
                .orElseThrow(() -> new ResourceNotFoundException("MembershipTier",
                        "categoryCode+tierCode", categoryCode + "/" + tierCode));
        if (req.getDisplayName() != null) tier.setDisplayName(req.getDisplayName());
        if (req.getDescription() != null) tier.setDescription(req.getDescription());
        if (req.getEnabled() != null) tier.setEnabled(req.getEnabled());
        if (req.getSortOrder() != null) tier.setSortOrder(req.getSortOrder());
        tier = tierRepo.save(tier);
        log.info("[Governance] Tier updated: {}/{} enabled={}",
                categoryCode, tier.getTierCode(), tier.getEnabled());
        return toTierResponse(tier);
    }

    @Transactional
    public void deleteTier(String categoryCode, String tierCode) {
        MembershipTierConfig tier = tierRepo.findByCategoryCodeAndTierCode(categoryCode, tierCode)
                .orElseThrow(() -> new ResourceNotFoundException("MembershipTier",
                        "categoryCode+tierCode", categoryCode + "/" + tierCode));
        if (Boolean.TRUE.equals(tier.getSystem())) {
            throw new BusinessRuleException("System tiers cannot be deleted. Disable instead.");
        }
        tierEntitlementRepo.deleteByCategoryCodeAndTierCode(categoryCode, tierCode);
        tierRepo.delete(tier);
        log.warn("[Governance] Tier deleted: {}/{}", categoryCode, tierCode);
    }

    // ── Mapping helpers ─────────────────────────────────────────────────────

    private CategoryResponse toCategoryResponse(MembershipCategoryConfig c, List<TierResponse> tiers) {
        return CategoryResponse.builder()
                .id(c.getId() != null ? c.getId().toString() : null)
                .code(c.getCode())
                .displayName(c.getDisplayName())
                .description(c.getDescription())
                .enabled(c.getEnabled())
                .sortOrder(c.getSortOrder())
                .system(c.getSystem())
                .tiers(tiers)
                .build();
    }

    private TierResponse toTierResponse(MembershipTierConfig t) {
        return TierResponse.builder()
                .id(t.getId() != null ? t.getId().toString() : null)
                .categoryCode(t.getCategoryCode())
                .tierCode(t.getTierCode())
                .displayName(t.getDisplayName())
                .description(t.getDescription())
                .enabled(t.getEnabled())
                .sortOrder(t.getSortOrder())
                .system(t.getSystem())
                .build();
    }
}
