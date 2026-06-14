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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MembershipGovernanceServiceTest {

    private MembershipCategoryConfigRepository categoryRepo;
    private MembershipTierConfigRepository tierRepo;
    private TierEntitlementRepository tierEntitlementRepo;
    private MembershipGovernanceService service;

    @BeforeEach
    void setUp() {
        categoryRepo = mock(MembershipCategoryConfigRepository.class);
        tierRepo = mock(MembershipTierConfigRepository.class);
        tierEntitlementRepo = mock(TierEntitlementRepository.class);
        service = new MembershipGovernanceService(categoryRepo, tierRepo, tierEntitlementRepo);
    }

    private MembershipCategoryConfig category(String code, boolean system) {
        return MembershipCategoryConfig.builder()
                .id(UUID.randomUUID()).code(code).displayName(code).enabled(true)
                .sortOrder(10).system(system).build();
    }

    private MembershipTierConfig tier(String catCode, String tierCode, boolean system) {
        return MembershipTierConfig.builder()
                .id(UUID.randomUUID()).categoryCode(catCode).tierCode(tierCode)
                .displayName(tierCode).enabled(true).sortOrder(10).system(system).build();
    }

    // ── Category list/get ─────────────────────────────────────────────

    @Test
    void listCategoriesWithTiers_attachesTierListPerCategory() {
        MembershipCategoryConfig cat = category("PERSONAL", true);
        when(categoryRepo.findAllByOrderBySortOrderAscCodeAsc()).thenReturn(List.of(cat));
        when(tierRepo.findByCategoryCodeOrderBySortOrderAscTierCodeAsc("PERSONAL"))
                .thenReturn(List.of(tier("PERSONAL", "FREE", true), tier("PERSONAL", "PRO", false)));

        List<CategoryResponse> out = service.listCategoriesWithTiers();

        assertThat(out).hasSize(1);
        assertThat(out.get(0).getCode()).isEqualTo("PERSONAL");
        assertThat(out.get(0).getTiers()).extracting(TierResponse::getTierCode)
                .containsExactly("FREE", "PRO");
    }

    @Test
    void getCategory_unknown_throws() {
        when(categoryRepo.findByCode("X")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getCategory("X"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getCategory_returnsResponseWithTiers() {
        when(categoryRepo.findByCode("PERSONAL")).thenReturn(Optional.of(category("PERSONAL", true)));
        when(tierRepo.findByCategoryCodeOrderBySortOrderAscTierCodeAsc("PERSONAL"))
                .thenReturn(List.of(tier("PERSONAL", "FREE", true)));

        CategoryResponse resp = service.getCategory("PERSONAL");

        assertThat(resp.getCode()).isEqualTo("PERSONAL");
        assertThat(resp.getTiers()).hasSize(1);
    }

    // ── createCategory ────────────────────────────────────────────────

    @Test
    void createCategory_blankCode_throws() {
        UpsertCategoryRequest req = new UpsertCategoryRequest();
        req.setCode("  ");

        assertThatThrownBy(() -> service.createCategory(req))
                .isInstanceOf(ValidationException.class);
        verify(categoryRepo, never()).save(any());
    }

    @Test
    void createCategory_nullCode_throws() {
        assertThatThrownBy(() -> service.createCategory(new UpsertCategoryRequest()))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void createCategory_duplicate_throws() {
        UpsertCategoryRequest req = new UpsertCategoryRequest();
        req.setCode("PERSONAL");
        when(categoryRepo.existsByCode("PERSONAL")).thenReturn(true);

        assertThatThrownBy(() -> service.createCategory(req))
                .isInstanceOf(DuplicateResourceException.class);
    }

    @Test
    void createCategory_appliesDefaults_andMarksNonSystem() {
        UpsertCategoryRequest req = new UpsertCategoryRequest();
        req.setCode("CUSTOM");
        when(categoryRepo.existsByCode("CUSTOM")).thenReturn(false);
        when(categoryRepo.save(any(MembershipCategoryConfig.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        CategoryResponse resp = service.createCategory(req);

        var captor = forClass(MembershipCategoryConfig.class);
        verify(categoryRepo).save(captor.capture());
        MembershipCategoryConfig saved = captor.getValue();
        assertThat(saved.getDisplayName()).isEqualTo("CUSTOM");  // displayName falls back to code
        assertThat(saved.getEnabled()).isTrue();
        assertThat(saved.getSortOrder()).isEqualTo(100);
        assertThat(saved.getSystem()).isFalse();                 // user-created can't be system
        assertThat(resp.getCode()).isEqualTo("CUSTOM");
        assertThat(resp.getTiers()).isEmpty();
    }

    @Test
    void createCategory_respectsExplicitFields() {
        UpsertCategoryRequest req = UpsertCategoryRequest.builder()
                .code("CUSTOM").displayName("My Custom").description("d")
                .enabled(false).sortOrder(7).build();
        when(categoryRepo.existsByCode("CUSTOM")).thenReturn(false);
        when(categoryRepo.save(any(MembershipCategoryConfig.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        service.createCategory(req);

        var captor = forClass(MembershipCategoryConfig.class);
        verify(categoryRepo).save(captor.capture());
        MembershipCategoryConfig saved = captor.getValue();
        assertThat(saved.getDisplayName()).isEqualTo("My Custom");
        assertThat(saved.getEnabled()).isFalse();
        assertThat(saved.getSortOrder()).isEqualTo(7);
        assertThat(saved.getDescription()).isEqualTo("d");
    }

    // ── updateCategory ────────────────────────────────────────────────

    @Test
    void updateCategory_unknown_throws() {
        when(categoryRepo.findByCode("X")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateCategory("X", new UpsertCategoryRequest()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void updateCategory_partialPatch_appliesNonNullOnly() {
        MembershipCategoryConfig existing = category("PERSONAL", true);
        existing.setDisplayName("Old");
        existing.setEnabled(true);
        existing.setSortOrder(10);
        when(categoryRepo.findByCode("PERSONAL")).thenReturn(Optional.of(existing));
        when(categoryRepo.save(any(MembershipCategoryConfig.class))).thenAnswer(inv -> inv.getArgument(0));
        when(tierRepo.findByCategoryCodeOrderBySortOrderAscTierCodeAsc("PERSONAL")).thenReturn(List.of());

        UpsertCategoryRequest patch = new UpsertCategoryRequest();
        patch.setEnabled(false); // only flip enabled

        service.updateCategory("PERSONAL", patch);

        assertThat(existing.getDisplayName()).isEqualTo("Old"); // unchanged
        assertThat(existing.getEnabled()).isFalse();
        assertThat(existing.getSortOrder()).isEqualTo(10);
    }

    // ── deleteCategory ────────────────────────────────────────────────

    @Test
    void deleteCategory_unknown_throws() {
        when(categoryRepo.findByCode("X")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deleteCategory("X"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void deleteCategory_systemCategory_throws() {
        when(categoryRepo.findByCode("PERSONAL")).thenReturn(Optional.of(category("PERSONAL", true)));

        assertThatThrownBy(() -> service.deleteCategory("PERSONAL"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("System categories");
        verify(categoryRepo, never()).delete(any());
    }

    @Test
    void deleteCategory_hasTiers_throws() {
        MembershipCategoryConfig cat = category("CUSTOM", false);
        when(categoryRepo.findByCode("CUSTOM")).thenReturn(Optional.of(cat));
        when(tierRepo.countByCategoryCode("CUSTOM")).thenReturn(2L);

        assertThatThrownBy(() -> service.deleteCategory("CUSTOM"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Remove tiers");
        verify(categoryRepo, never()).delete(any());
    }

    @Test
    void deleteCategory_nonSystemEmpty_deletes() {
        MembershipCategoryConfig cat = category("CUSTOM", false);
        when(categoryRepo.findByCode("CUSTOM")).thenReturn(Optional.of(cat));
        when(tierRepo.countByCategoryCode("CUSTOM")).thenReturn(0L);

        service.deleteCategory("CUSTOM");

        verify(categoryRepo).delete(cat);
    }

    // ── Tier operations ───────────────────────────────────────────────

    @Test
    void listTiers_unknownCategory_throws() {
        when(categoryRepo.findByCode("X")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.listTiers("X"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void listTiers_returnsCategoryTiers() {
        when(categoryRepo.findByCode("PERSONAL")).thenReturn(Optional.of(category("PERSONAL", true)));
        when(tierRepo.findByCategoryCodeOrderBySortOrderAscTierCodeAsc("PERSONAL"))
                .thenReturn(List.of(tier("PERSONAL", "FREE", true)));

        assertThat(service.listTiers("PERSONAL")).extracting(TierResponse::getTierCode)
                .containsExactly("FREE");
    }

    @Test
    void listAllTiers_sortsByCategoryThenSortOrderThenCode() {
        // Repo returns mixed-up ordering; service is responsible for stable sort.
        when(tierRepo.findAllByOrderByCategoryCodeAscSortOrderAscTierCodeAsc()).thenReturn(List.of(
                MembershipTierConfig.builder().categoryCode("PERSONAL").tierCode("PRO").sortOrder(20).build(),
                MembershipTierConfig.builder().categoryCode("EDUCATION").tierCode("FREE").sortOrder(10).build(),
                MembershipTierConfig.builder().categoryCode("PERSONAL").tierCode("FREE").sortOrder(10).build()
        ));

        List<TierResponse> out = service.listAllTiers();

        assertThat(out).extracting(t -> t.getCategoryCode() + "/" + t.getTierCode())
                .containsExactly("EDUCATION/FREE", "PERSONAL/FREE", "PERSONAL/PRO");
    }

    @Test
    void createTier_unknownCategory_throws() {
        when(categoryRepo.findByCode("X")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createTier("X", new UpsertTierRequest()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void createTier_blankTierCode_throws() {
        when(categoryRepo.findByCode("PERSONAL")).thenReturn(Optional.of(category("PERSONAL", true)));
        UpsertTierRequest req = new UpsertTierRequest();
        req.setTierCode("  ");

        assertThatThrownBy(() -> service.createTier("PERSONAL", req))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void createTier_duplicate_throws() {
        when(categoryRepo.findByCode("PERSONAL")).thenReturn(Optional.of(category("PERSONAL", true)));
        UpsertTierRequest req = UpsertTierRequest.builder().tierCode("FREE").build();
        when(tierRepo.existsByCategoryCodeAndTierCode("PERSONAL", "FREE")).thenReturn(true);

        assertThatThrownBy(() -> service.createTier("PERSONAL", req))
                .isInstanceOf(DuplicateResourceException.class);
    }

    @Test
    void createTier_appliesDefaultsAndMarksNonSystem() {
        when(categoryRepo.findByCode("PERSONAL")).thenReturn(Optional.of(category("PERSONAL", true)));
        UpsertTierRequest req = UpsertTierRequest.builder().tierCode("CUSTOM").build();
        when(tierRepo.existsByCategoryCodeAndTierCode("PERSONAL", "CUSTOM")).thenReturn(false);
        when(tierRepo.save(any(MembershipTierConfig.class))).thenAnswer(inv -> inv.getArgument(0));

        TierResponse resp = service.createTier("PERSONAL", req);

        var captor = forClass(MembershipTierConfig.class);
        verify(tierRepo).save(captor.capture());
        MembershipTierConfig saved = captor.getValue();
        assertThat(saved.getCategoryCode()).isEqualTo("PERSONAL");
        assertThat(saved.getDisplayName()).isEqualTo("CUSTOM");
        assertThat(saved.getEnabled()).isTrue();
        assertThat(saved.getSortOrder()).isEqualTo(100);
        assertThat(saved.getSystem()).isFalse();
        assertThat(resp.getTierCode()).isEqualTo("CUSTOM");
    }

    @Test
    void updateTier_unknown_throws() {
        when(tierRepo.findByCategoryCodeAndTierCode("PERSONAL", "GHOST"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateTier("PERSONAL", "GHOST", new UpsertTierRequest()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void updateTier_partialPatch_appliesNonNullOnly() {
        MembershipTierConfig existing = tier("PERSONAL", "FREE", true);
        existing.setDisplayName("Old");
        existing.setSortOrder(10);
        existing.setEnabled(true);
        when(tierRepo.findByCategoryCodeAndTierCode("PERSONAL", "FREE"))
                .thenReturn(Optional.of(existing));
        when(tierRepo.save(any(MembershipTierConfig.class))).thenAnswer(inv -> inv.getArgument(0));

        UpsertTierRequest patch = UpsertTierRequest.builder().displayName("Free Tier").build();

        service.updateTier("PERSONAL", "FREE", patch);

        assertThat(existing.getDisplayName()).isEqualTo("Free Tier");
        assertThat(existing.getEnabled()).isTrue();   // unchanged
        assertThat(existing.getSortOrder()).isEqualTo(10);  // unchanged
    }

    @Test
    void deleteTier_unknown_throws() {
        when(tierRepo.findByCategoryCodeAndTierCode("PERSONAL", "GHOST"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deleteTier("PERSONAL", "GHOST"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void deleteTier_systemTier_throws() {
        when(tierRepo.findByCategoryCodeAndTierCode("PERSONAL", "FREE"))
                .thenReturn(Optional.of(tier("PERSONAL", "FREE", true)));

        assertThatThrownBy(() -> service.deleteTier("PERSONAL", "FREE"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("System tiers");
        verify(tierRepo, never()).delete(any());
        verify(tierEntitlementRepo, never()).deleteByCategoryCodeAndTierCode(any(), any());
    }

    @Test
    void deleteTier_nonSystem_clearsEntitlementsThenDeletes() {
        MembershipTierConfig t = tier("PERSONAL", "CUSTOM", false);
        when(tierRepo.findByCategoryCodeAndTierCode("PERSONAL", "CUSTOM"))
                .thenReturn(Optional.of(t));

        service.deleteTier("PERSONAL", "CUSTOM");

        // Entitlement rows must be cleared before the tier itself goes away,
        // otherwise an FK or orphan-row issue could leave dangling entitlements.
        verify(tierEntitlementRepo).deleteByCategoryCodeAndTierCode("PERSONAL", "CUSTOM");
        verify(tierRepo).delete(t);
    }
}
