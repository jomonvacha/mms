package com.roots.mms.service;

import com.roots.mms.entity.AiModel;
import com.roots.mms.entity.AiModelProvider;
import com.roots.mms.entity.AiModelTierBinding;
import com.roots.mms.entity.Member;
import com.roots.mms.repository.AiModelProviderRepository;
import com.roots.mms.repository.AiModelRepository;
import com.roots.mms.repository.AiModelTierBindingRepository;
import com.roots.mms.repository.MemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Pure unit test of the {@link AiModelService#resolveForMember(String)} matrix:
 * which models surface for a member, which are included vs locked, and which
 * are filtered out entirely. No Spring context — the resolver is deterministic
 * business logic and the test stays fast.
 */
class AiModelServiceTest {

    private AiModelRepository modelRepo;
    private AiModelTierBindingRepository bindingRepo;
    private AiModelProviderRepository providerRepo;
    private MemberRepository memberRepo;
    private AiModelService service;

    @BeforeEach
    void setUp() {
        modelRepo = Mockito.mock(AiModelRepository.class);
        bindingRepo = Mockito.mock(AiModelTierBindingRepository.class);
        providerRepo = Mockito.mock(AiModelProviderRepository.class);
        memberRepo = Mockito.mock(MemberRepository.class);
        service = new AiModelService(providerRepo, modelRepo, bindingRepo, memberRepo);
    }

    @Test
    void resolveForMember_returnsIncludedAndLockedWithCorrectFlags() {
        String userId = "00000000-0000-0000-0000-000000000001";
        givenMember(userId, "PERSONAL", "PRO");
        givenModels(
                model("gpt-5.4-nano",  1, 10, AiModel.LockStyle.LOCK),
                model("gpt-5.4-mini",  2, 20, AiModel.LockStyle.LOCK),
                model("gpt-5.4",       3, 30, AiModel.LockStyle.LOCK),
                model("gpt-5.4-pro",   4, 40, AiModel.LockStyle.LOCK)
        );
        givenBindings("PERSONAL", "PRO",
                bind("gpt-5.4-nano", false),
                bind("gpt-5.4-mini", true),
                bind("gpt-5.4",      false)
                // gpt-5.4-pro has no binding → should surface locked
        );

        var catalog = service.resolveForMember(userId);

        assertThat(catalog.categoryCode()).isEqualTo("PERSONAL");
        assertThat(catalog.tierCode()).isEqualTo("PRO");
        assertThat(catalog.defaultModelCode()).isEqualTo("gpt-5.4-mini");
        assertThat(catalog.models()).extracting(AiModelService.ResolvedModelView::code)
                .containsExactly("gpt-5.4-nano", "gpt-5.4-mini", "gpt-5.4", "gpt-5.4-pro");

        var byCode = codeMap(catalog.models());
        assertThat(byCode.get("gpt-5.4-nano").included()).isTrue();
        assertThat(byCode.get("gpt-5.4-nano").locked()).isFalse();
        assertThat(byCode.get("gpt-5.4-mini").isDefault()).isTrue();
        assertThat(byCode.get("gpt-5.4-pro").included()).isFalse();
        assertThat(byCode.get("gpt-5.4-pro").locked()).isTrue();
        assertThat(byCode.get("gpt-5.4-pro").lockReason()).isEqualTo("PREMIUM");
    }

    @Test
    void resolveForMember_dropsHideStyledModelsWhenNotIncluded() {
        String userId = "00000000-0000-0000-0000-000000000002";
        givenMember(userId, "PERSONAL", "FREE");
        givenModels(
                model("gpt-5.4-nano", 1, 10, AiModel.LockStyle.LOCK),
                model("secret-model", 4, 99, AiModel.LockStyle.HIDE)
        );
        givenBindings("PERSONAL", "FREE",
                bind("gpt-5.4-nano", true)
                // secret-model not bound + lockStyle=HIDE → never appears
        );

        var catalog = service.resolveForMember(userId);

        assertThat(catalog.models()).extracting(AiModelService.ResolvedModelView::code)
                .containsExactly("gpt-5.4-nano");
    }

    @Test
    void resolveForMember_excludesNonEnabledModels() {
        String userId = "00000000-0000-0000-0000-000000000003";
        givenMember(userId, "PERSONAL", "PRO");
        AiModel disabled = model("gpt-5.4-mini", 2, 20, AiModel.LockStyle.LOCK);
        disabled.setStatus(AiModel.Status.DISABLED);
        AiModel deprecated = model("gpt-5.3", 2, 25, AiModel.LockStyle.LOCK);
        deprecated.setStatus(AiModel.Status.DEPRECATED);
        givenModels(
                model("gpt-5.4-nano", 1, 10, AiModel.LockStyle.LOCK),
                disabled,
                deprecated
        );
        givenBindings("PERSONAL", "PRO",
                bind("gpt-5.4-nano", true),
                bind("gpt-5.4-mini", false),
                bind("gpt-5.3",      false)
        );

        var catalog = service.resolveForMember(userId);

        assertThat(catalog.models()).extracting(AiModelService.ResolvedModelView::code)
                .containsExactly("gpt-5.4-nano");
    }

    @Test
    void resolveForMember_memberWithoutTier_returnsAllModelsAsLocked() {
        // A member with no categoryCode/tierCode (e.g. just-signed-up, not yet
        // assigned) should see the catalog but nothing included — matches the
        // "degrade, don't widen" rule.
        String userId = "00000000-0000-0000-0000-000000000004";
        Member m = new Member();
        m.setUserId(UUID.fromString(userId));
        when(memberRepo.findByUserId(UUID.fromString(userId))).thenReturn(Optional.of(m));
        givenModels(
                model("gpt-5.4-nano", 1, 10, AiModel.LockStyle.LOCK),
                model("gpt-5.4-pro",  4, 40, AiModel.LockStyle.LOCK)
        );
        when(bindingRepo.findByCategoryCodeAndTierCode(any(), any())).thenReturn(List.of());

        var catalog = service.resolveForMember(userId);

        assertThat(catalog.defaultModelCode()).isNull();
        assertThat(catalog.models()).hasSize(2);
        assertThat(catalog.models()).allMatch(v -> !v.included());
        assertThat(catalog.models()).allMatch(AiModelService.ResolvedModelView::locked);
    }

    @Test
    void resolveForMember_sortsByModelSortOrder() {
        String userId = "00000000-0000-0000-0000-000000000005";
        givenMember(userId, "PERSONAL", "PRO");
        // Insert in random order to prove the resolver sorts output.
        givenModels(
                model("c-model", 2, 30, AiModel.LockStyle.LOCK),
                model("a-model", 2, 10, AiModel.LockStyle.LOCK),
                model("b-model", 2, 20, AiModel.LockStyle.LOCK)
        );
        givenBindings("PERSONAL", "PRO",
                bind("a-model", false),
                bind("b-model", true),
                bind("c-model", false)
        );

        var catalog = service.resolveForMember(userId);

        assertThat(catalog.models()).extracting(AiModelService.ResolvedModelView::code)
                .containsExactly("a-model", "b-model", "c-model");
    }

    // ── helpers ─────────────────────────────────────────────────────

    private static AiModel model(String code, int qualityLevel, int sortOrder, AiModel.LockStyle style) {
        return AiModel.builder()
                .code(code)
                .providerCode("openai")
                .displayName(code)
                .qualityLevel(qualityLevel)
                .costLevel(qualityLevel)
                .sortOrder(sortOrder)
                .status(AiModel.Status.ENABLED)
                .lockStyle(style)
                .build();
    }

    private static AiModelTierBinding bind(String modelCode, boolean isDefault) {
        return AiModelTierBinding.builder()
                .modelCode(modelCode)
                .isDefault(isDefault)
                .build();
    }

    private void givenMember(String userId, String category, String tier) {
        Member m = new Member();
        m.setUserId(UUID.fromString(userId));
        m.setCategoryCode(category);
        m.setTierCode(tier);
        when(memberRepo.findByUserId(UUID.fromString(userId))).thenReturn(Optional.of(m));
    }

    private void givenModels(AiModel... models) {
        when(modelRepo.findAllByOrderBySortOrderAscDisplayNameAsc()).thenReturn(List.of(models));
    }

    private void givenBindings(String category, String tier, AiModelTierBinding... bindings) {
        for (AiModelTierBinding b : bindings) {
            b.setCategoryCode(category);
            b.setTierCode(tier);
        }
        when(bindingRepo.findByCategoryCodeAndTierCode(eq(category), eq(tier)))
                .thenReturn(List.of(bindings));
    }

    private static java.util.Map<String, AiModelService.ResolvedModelView> codeMap(
            List<AiModelService.ResolvedModelView> views) {
        var map = new java.util.HashMap<String, AiModelService.ResolvedModelView>();
        for (var v : views) map.put(v.code(), v);
        return map;
    }

    @SuppressWarnings("unused")
    private AiModelProvider openai() {
        return AiModelProvider.builder().code("openai").displayName("OpenAI").enabled(true).build();
    }
}
