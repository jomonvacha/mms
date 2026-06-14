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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
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

class EntitlementServiceTest {

    private EntitlementRepository entitlementRepo;
    private TierEntitlementRepository tierEntitlementRepo;
    private MembershipCategoryConfigRepository categoryRepo;
    private MembershipTierConfigRepository tierRepo;
    private MemberRepository memberRepository;
    private EntitlementService service;

    @BeforeEach
    void setUp() {
        entitlementRepo = mock(EntitlementRepository.class);
        tierEntitlementRepo = mock(TierEntitlementRepository.class);
        categoryRepo = mock(MembershipCategoryConfigRepository.class);
        tierRepo = mock(MembershipTierConfigRepository.class);
        memberRepository = mock(MemberRepository.class);
        service = new EntitlementService(entitlementRepo, tierEntitlementRepo,
                categoryRepo, tierRepo, memberRepository);
    }

    private Entitlement entitlement(String key, Entitlement.ValueType type, String defaultValue, boolean system) {
        return Entitlement.builder()
                .id(UUID.randomUUID()).key(key).displayName(key).valueType(type)
                .defaultValue(defaultValue).system(system).build();
    }

    // ── listEntitlements ──────────────────────────────────────────────

    @Test
    void listEntitlements_sortsByKey() {
        when(entitlementRepo.findAll()).thenReturn(List.of(
                entitlement("z.key", Entitlement.ValueType.BOOLEAN, "false", false),
                entitlement("a.key", Entitlement.ValueType.INTEGER, "0", true)
        ));

        List<EntitlementResponse> out = service.listEntitlements();

        assertThat(out).extracting(EntitlementResponse::getKey).containsExactly("a.key", "z.key");
    }

    // ── upsertEntitlement ─────────────────────────────────────────────

    @Test
    void upsertEntitlement_blankKey_throws() {
        Entitlement e = Entitlement.builder().key("  ").build();

        assertThatThrownBy(() -> service.upsertEntitlement(e))
                .isInstanceOf(ValidationException.class);
        verify(entitlementRepo, never()).save(any());
    }

    @Test
    void upsertEntitlement_nullKey_throws() {
        assertThatThrownBy(() -> service.upsertEntitlement(new Entitlement()))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void upsertEntitlement_newKey_savesWithSystemFalse() {
        Entitlement incoming = entitlement("new.key", Entitlement.ValueType.BOOLEAN, "false", true);
        when(entitlementRepo.findByKey("new.key")).thenReturn(Optional.empty());
        when(entitlementRepo.save(any(Entitlement.class))).thenAnswer(inv -> inv.getArgument(0));

        service.upsertEntitlement(incoming);

        // Newly upserted entitlements are forced to system=false even if the
        // caller tried to mark them system. Only the seed path may create
        // system entitlements.
        assertThat(incoming.getSystem()).isFalse();
        verify(entitlementRepo).save(incoming);
    }

    @Test
    void upsertEntitlement_existingKey_appliesNonNullFields() {
        Entitlement existing = entitlement("k", Entitlement.ValueType.BOOLEAN, "false", true);
        existing.setDisplayName("Old");
        existing.setCategory("old-cat");
        when(entitlementRepo.findByKey("k")).thenReturn(Optional.of(existing));
        when(entitlementRepo.save(any(Entitlement.class))).thenAnswer(inv -> inv.getArgument(0));

        Entitlement patch = Entitlement.builder()
                .key("k").displayName("New").description("desc").build();

        service.upsertEntitlement(patch);

        assertThat(existing.getDisplayName()).isEqualTo("New");
        assertThat(existing.getDescription()).isEqualTo("desc");
        assertThat(existing.getCategory()).isEqualTo("old-cat"); // unchanged
        // System flag must NOT be flipped via upsert. The original system=true
        // must survive an admin edit.
        assertThat(existing.getSystem()).isTrue();
    }

    // ── createEntitlement ─────────────────────────────────────────────

    @Test
    void createEntitlement_duplicate_throws() {
        Entitlement e = entitlement("dup", Entitlement.ValueType.BOOLEAN, "false", false);
        when(entitlementRepo.existsByKey("dup")).thenReturn(true);

        assertThatThrownBy(() -> service.createEntitlement(e))
                .isInstanceOf(DuplicateResourceException.class);
    }

    @Test
    void createEntitlement_unique_delegatesToUpsert() {
        Entitlement e = entitlement("new", Entitlement.ValueType.BOOLEAN, "false", false);
        when(entitlementRepo.existsByKey("new")).thenReturn(false);
        when(entitlementRepo.findByKey("new")).thenReturn(Optional.empty());
        when(entitlementRepo.save(any(Entitlement.class))).thenAnswer(inv -> inv.getArgument(0));

        EntitlementResponse resp = service.createEntitlement(e);

        assertThat(resp.getKey()).isEqualTo("new");
        verify(entitlementRepo).save(e);
    }

    // ── deleteEntitlement ─────────────────────────────────────────────

    @Test
    void deleteEntitlement_unknown_throws() {
        when(entitlementRepo.findByKey("x")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deleteEntitlement("x"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void deleteEntitlement_system_throws() {
        when(entitlementRepo.findByKey("k")).thenReturn(Optional.of(
                entitlement("k", Entitlement.ValueType.BOOLEAN, "false", true)));

        assertThatThrownBy(() -> service.deleteEntitlement("k"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("System entitlements");
        verify(entitlementRepo, never()).delete(any());
    }

    @Test
    void deleteEntitlement_nonSystem_clearsTierEntitlementsAndDeletes() {
        Entitlement e = entitlement("k", Entitlement.ValueType.BOOLEAN, "false", false);
        when(entitlementRepo.findByKey("k")).thenReturn(Optional.of(e));

        service.deleteEntitlement("k");

        verify(tierEntitlementRepo).deleteByEntitlementKey("k");
        verify(entitlementRepo).delete(e);
    }

    // ── getTierValues ─────────────────────────────────────────────────

    @Test
    void getTierValues_unknownTier_throws() {
        when(tierRepo.findByCategoryCodeAndTierCode("PERSONAL", "GHOST"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getTierValues("PERSONAL", "GHOST"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getTierValues_returnsMap() {
        when(tierRepo.findByCategoryCodeAndTierCode("PERSONAL", "FREE"))
                .thenReturn(Optional.of(MembershipTierConfig.builder().build()));
        when(tierEntitlementRepo.findByCategoryCodeAndTierCode("PERSONAL", "FREE")).thenReturn(List.of(
                TierEntitlement.builder().entitlementKey("a").value("1").build(),
                TierEntitlement.builder().entitlementKey("b").value("true").build()
        ));

        assertThat(service.getTierValues("PERSONAL", "FREE"))
                .containsEntry("a", "1").containsEntry("b", "true");
    }

    // ── setTierEntitlement ────────────────────────────────────────────

    @Test
    void setTierEntitlement_unknownTier_throws() {
        when(tierRepo.findByCategoryCodeAndTierCode("PERSONAL", "GHOST"))
                .thenReturn(Optional.empty());

        UpsertTierEntitlementRequest req = UpsertTierEntitlementRequest.builder()
                .entitlementKey("k").value("true").build();

        assertThatThrownBy(() -> service.setTierEntitlement("PERSONAL", "GHOST", req))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void setTierEntitlement_unknownEntitlement_throws() {
        when(tierRepo.findByCategoryCodeAndTierCode("PERSONAL", "FREE"))
                .thenReturn(Optional.of(MembershipTierConfig.builder().build()));
        when(entitlementRepo.findByKey("missing.key")).thenReturn(Optional.empty());

        UpsertTierEntitlementRequest req = UpsertTierEntitlementRequest.builder()
                .entitlementKey("missing.key").value("true").build();

        assertThatThrownBy(() -> service.setTierEntitlement("PERSONAL", "FREE", req))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void setTierEntitlement_booleanValueTypeRejectsNonBoolean() {
        Entitlement def = entitlement("k", Entitlement.ValueType.BOOLEAN, "false", false);
        when(tierRepo.findByCategoryCodeAndTierCode("PERSONAL", "FREE"))
                .thenReturn(Optional.of(MembershipTierConfig.builder().build()));
        when(entitlementRepo.findByKey("k")).thenReturn(Optional.of(def));

        UpsertTierEntitlementRequest req = UpsertTierEntitlementRequest.builder()
                .entitlementKey("k").value("yes").build();

        assertThatThrownBy(() -> service.setTierEntitlement("PERSONAL", "FREE", req))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("BOOLEAN");
    }

    @Test
    void setTierEntitlement_booleanAcceptsCaseInsensitiveTrueFalse() {
        Entitlement def = entitlement("k", Entitlement.ValueType.BOOLEAN, "false", false);
        when(tierRepo.findByCategoryCodeAndTierCode("PERSONAL", "FREE"))
                .thenReturn(Optional.of(MembershipTierConfig.builder().build()));
        when(entitlementRepo.findByKey("k")).thenReturn(Optional.of(def));
        when(tierEntitlementRepo.findByCategoryCodeAndTierCodeAndEntitlementKey(
                "PERSONAL", "FREE", "k")).thenReturn(Optional.empty());

        service.setTierEntitlement("PERSONAL", "FREE",
                UpsertTierEntitlementRequest.builder().entitlementKey("k").value("FALSE").build());

        var captor = forClass(TierEntitlement.class);
        verify(tierEntitlementRepo).save(captor.capture());
        assertThat(captor.getValue().getValue()).isEqualTo("FALSE");
    }

    @Test
    void setTierEntitlement_integerValueTypeRejectsNonNumeric() {
        Entitlement def = entitlement("k", Entitlement.ValueType.INTEGER, "0", false);
        when(tierRepo.findByCategoryCodeAndTierCode("PERSONAL", "FREE"))
                .thenReturn(Optional.of(MembershipTierConfig.builder().build()));
        when(entitlementRepo.findByKey("k")).thenReturn(Optional.of(def));

        UpsertTierEntitlementRequest req = UpsertTierEntitlementRequest.builder()
                .entitlementKey("k").value("3.14").build();

        assertThatThrownBy(() -> service.setTierEntitlement("PERSONAL", "FREE", req))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("whole number");
    }

    @Test
    void setTierEntitlement_stringValueTypeAcceptsAnything() {
        Entitlement def = entitlement("k", Entitlement.ValueType.STRING, "", false);
        when(tierRepo.findByCategoryCodeAndTierCode("PERSONAL", "FREE"))
                .thenReturn(Optional.of(MembershipTierConfig.builder().build()));
        when(entitlementRepo.findByKey("k")).thenReturn(Optional.of(def));
        when(tierEntitlementRepo.findByCategoryCodeAndTierCodeAndEntitlementKey(
                "PERSONAL", "FREE", "k")).thenReturn(Optional.empty());

        service.setTierEntitlement("PERSONAL", "FREE",
                UpsertTierEntitlementRequest.builder().entitlementKey("k").value("anything goes").build());

        verify(tierEntitlementRepo).save(any(TierEntitlement.class));
    }

    @Test
    void setTierEntitlement_nullValue_throws() {
        Entitlement def = entitlement("k", Entitlement.ValueType.STRING, "", false);
        when(tierRepo.findByCategoryCodeAndTierCode("PERSONAL", "FREE"))
                .thenReturn(Optional.of(MembershipTierConfig.builder().build()));
        when(entitlementRepo.findByKey("k")).thenReturn(Optional.of(def));

        UpsertTierEntitlementRequest req = UpsertTierEntitlementRequest.builder()
                .entitlementKey("k").value(null).build();

        assertThatThrownBy(() -> service.setTierEntitlement("PERSONAL", "FREE", req))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void setTierEntitlement_existingBinding_updatesValue() {
        Entitlement def = entitlement("k", Entitlement.ValueType.INTEGER, "0", false);
        TierEntitlement existing = TierEntitlement.builder()
                .categoryCode("PERSONAL").tierCode("FREE").entitlementKey("k").value("5").build();
        when(tierRepo.findByCategoryCodeAndTierCode("PERSONAL", "FREE"))
                .thenReturn(Optional.of(MembershipTierConfig.builder().build()));
        when(entitlementRepo.findByKey("k")).thenReturn(Optional.of(def));
        when(tierEntitlementRepo.findByCategoryCodeAndTierCodeAndEntitlementKey(
                "PERSONAL", "FREE", "k")).thenReturn(Optional.of(existing));

        service.setTierEntitlement("PERSONAL", "FREE",
                UpsertTierEntitlementRequest.builder().entitlementKey("k").value("42").build());

        assertThat(existing.getValue()).isEqualTo("42");
        verify(tierEntitlementRepo).save(existing);
    }

    // ── removeTierEntitlement ─────────────────────────────────────────

    @Test
    void removeTierEntitlement_unknown_throws() {
        when(tierEntitlementRepo.findByCategoryCodeAndTierCodeAndEntitlementKey(
                "PERSONAL", "FREE", "k")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.removeTierEntitlement("PERSONAL", "FREE", "k"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void removeTierEntitlement_existing_deletes() {
        TierEntitlement t = TierEntitlement.builder()
                .categoryCode("PERSONAL").tierCode("FREE").entitlementKey("k").build();
        when(tierEntitlementRepo.findByCategoryCodeAndTierCodeAndEntitlementKey(
                "PERSONAL", "FREE", "k")).thenReturn(Optional.of(t));

        service.removeTierEntitlement("PERSONAL", "FREE", "k");

        verify(tierEntitlementRepo).delete(t);
    }

    // ── requireByKey ──────────────────────────────────────────────────

    @Test
    void requireByKey_unknown_throws() {
        when(entitlementRepo.findByKey("k")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.requireByKey("k"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void requireByKey_returns() {
        Entitlement e = entitlement("k", Entitlement.ValueType.BOOLEAN, "false", false);
        when(entitlementRepo.findByKey("k")).thenReturn(Optional.of(e));

        assertThat(service.requireByKey("k")).isSameAs(e);
    }

    // ── resolveForMember ──────────────────────────────────────────────

    @Test
    void resolveForMember_noMember_returnsAllDefaults() {
        UUID userId = UUID.randomUUID();
        when(memberRepository.findByUserId(userId)).thenReturn(Optional.empty());
        when(entitlementRepo.findAll()).thenReturn(List.of(
                entitlement("a", Entitlement.ValueType.BOOLEAN, "false", false),
                entitlement("b", Entitlement.ValueType.INTEGER, "10", false)
        ));

        ResolvedEntitlementsResponse resp = service.resolveForMember(userId.toString());

        assertThat(resp.getCategoryCode()).isNull();
        assertThat(resp.getTierCode()).isNull();
        assertThat(resp.getCategoryEnabled()).isNull();
        assertThat(resp.getTierEnabled()).isNull();
        assertThat(resp.getEntitlements()).containsEntry("a", "false").containsEntry("b", "10");
    }

    @Test
    void resolveForMember_disabledCategory_fallsBackToDefaults() {
        UUID userId = UUID.randomUUID();
        Member m = new Member();
        m.setUserId(userId);
        m.setCategoryCode("PERSONAL");
        m.setTierCode("FREE");
        when(memberRepository.findByUserId(userId)).thenReturn(Optional.of(m));
        when(entitlementRepo.findAll()).thenReturn(List.of(
                entitlement("a", Entitlement.ValueType.BOOLEAN, "false", false)));
        when(categoryRepo.findByCode("PERSONAL")).thenReturn(Optional.of(
                MembershipCategoryConfig.builder().code("PERSONAL").enabled(false).build()));
        when(tierRepo.findByCategoryCodeAndTierCode("PERSONAL", "FREE"))
                .thenReturn(Optional.of(MembershipTierConfig.builder().enabled(true).build()));

        ResolvedEntitlementsResponse resp = service.resolveForMember(userId.toString());

        assertThat(resp.getCategoryEnabled()).isFalse();
        // Critical: tier overrides must NOT be layered when the category is
        // disabled — a deactivated category must degrade access to defaults.
        verify(tierEntitlementRepo, never()).findByCategoryCodeAndTierCode(any(), any());
        assertThat(resp.getEntitlements()).containsEntry("a", "false");
    }

    @Test
    void resolveForMember_disabledTier_fallsBackToDefaults() {
        UUID userId = UUID.randomUUID();
        Member m = new Member();
        m.setUserId(userId);
        m.setCategoryCode("PERSONAL");
        m.setTierCode("FREE");
        when(memberRepository.findByUserId(userId)).thenReturn(Optional.of(m));
        when(entitlementRepo.findAll()).thenReturn(List.of());
        when(categoryRepo.findByCode("PERSONAL")).thenReturn(Optional.of(
                MembershipCategoryConfig.builder().enabled(true).build()));
        when(tierRepo.findByCategoryCodeAndTierCode("PERSONAL", "FREE"))
                .thenReturn(Optional.of(MembershipTierConfig.builder().enabled(false).build()));

        ResolvedEntitlementsResponse resp = service.resolveForMember(userId.toString());

        assertThat(resp.getTierEnabled()).isFalse();
        verify(tierEntitlementRepo, never()).findByCategoryCodeAndTierCode(any(), any());
    }

    @Test
    void resolveForMember_enabledCategoryAndTier_layersOverrides() {
        UUID userId = UUID.randomUUID();
        Member m = new Member();
        m.setUserId(userId);
        m.setCategoryCode("PERSONAL");
        m.setTierCode("PRO");
        when(memberRepository.findByUserId(userId)).thenReturn(Optional.of(m));
        when(entitlementRepo.findAll()).thenReturn(List.of(
                entitlement("a", Entitlement.ValueType.BOOLEAN, "false", false),
                entitlement("b", Entitlement.ValueType.INTEGER, "10", false)
        ));
        when(categoryRepo.findByCode("PERSONAL")).thenReturn(Optional.of(
                MembershipCategoryConfig.builder().enabled(true).build()));
        when(tierRepo.findByCategoryCodeAndTierCode("PERSONAL", "PRO"))
                .thenReturn(Optional.of(MembershipTierConfig.builder().enabled(true).build()));
        when(tierEntitlementRepo.findByCategoryCodeAndTierCode("PERSONAL", "PRO")).thenReturn(List.of(
                TierEntitlement.builder().entitlementKey("a").value("true").build(),
                // 'b' has no tier override — should fall through to default "10".
                TierEntitlement.builder().entitlementKey("c").value("orphan").build() // not in entitlements list
        ));

        ResolvedEntitlementsResponse resp = service.resolveForMember(userId.toString());

        Map<String, String> entitlements = resp.getEntitlements();
        assertThat(entitlements).containsEntry("a", "true");      // overridden
        assertThat(entitlements).containsEntry("b", "10");        // default kept
        // Orphan tier value (no matching definition) is still surfaced — the
        // resolver doesn't filter against the definition list. Documents
        // current behavior; if we ever decide to drop orphans, flip this test.
        assertThat(entitlements).containsEntry("c", "orphan");
        assertThat(resp.getCategoryEnabled()).isTrue();
        assertThat(resp.getTierEnabled()).isTrue();
    }

    @Test
    void resolveForMember_memberWithNullTier_fallsBackToDefaults() {
        UUID userId = UUID.randomUUID();
        Member m = new Member();
        m.setUserId(userId);
        m.setCategoryCode("PERSONAL");
        m.setTierCode(null); // hasn't picked a tier yet
        when(memberRepository.findByUserId(userId)).thenReturn(Optional.of(m));
        when(entitlementRepo.findAll()).thenReturn(List.of(
                entitlement("a", Entitlement.ValueType.BOOLEAN, "false", false)));

        ResolvedEntitlementsResponse resp = service.resolveForMember(userId.toString());

        assertThat(resp.getCategoryEnabled()).isNull();
        assertThat(resp.getTierEnabled()).isNull();
        assertThat(resp.getEntitlements()).containsEntry("a", "false");
        verify(categoryRepo, never()).findByCode(any());
    }

    @Test
    void resolveForMember_categoryMissing_treatedAsDisabled() {
        UUID userId = UUID.randomUUID();
        Member m = new Member();
        m.setUserId(userId);
        m.setCategoryCode("STALE");
        m.setTierCode("FREE");
        when(memberRepository.findByUserId(userId)).thenReturn(Optional.of(m));
        when(entitlementRepo.findAll()).thenReturn(List.of());
        when(categoryRepo.findByCode("STALE")).thenReturn(Optional.empty());
        when(tierRepo.findByCategoryCodeAndTierCode("STALE", "FREE")).thenReturn(Optional.empty());

        ResolvedEntitlementsResponse resp = service.resolveForMember(userId.toString());

        // Defensive: stale category/tier reference must not crash and must not
        // grant anything beyond defaults.
        assertThat(resp.getCategoryEnabled()).isFalse();
        assertThat(resp.getTierEnabled()).isFalse();
    }
}
