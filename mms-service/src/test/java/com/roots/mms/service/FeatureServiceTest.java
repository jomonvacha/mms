package com.roots.mms.service;

import com.roots.mms.entity.Feature;
import com.roots.mms.entity.RoleFeatureMap;
import com.roots.mms.exception.BusinessRuleException;
import com.roots.mms.exception.DuplicateResourceException;
import com.roots.mms.exception.ResourceNotFoundException;
import com.roots.mms.repository.FeatureRepository;
import com.roots.mms.repository.RoleFeatureMapRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FeatureServiceTest {

    private FeatureRepository featureRepo;
    private RoleFeatureMapRepository mapRepo;
    private FeatureService service;

    @BeforeEach
    void setUp() {
        featureRepo = mock(FeatureRepository.class);
        mapRepo = mock(RoleFeatureMapRepository.class);
        service = new FeatureService(featureRepo, mapRepo);
    }

    // ── Create ─────────────────────────────────────────────────────────

    @Test
    void create_blankCode_throws() {
        Feature f = Feature.builder().code("   ").name("X").build();

        assertThatThrownBy(() -> service.create(f))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("code is required");
        verify(featureRepo, never()).save(any());
    }

    @Test
    void create_nullCode_throws() {
        Feature f = Feature.builder().name("X").build();
        assertThatThrownBy(() -> service.create(f)).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void create_normalizesCodeToLowercaseSnake() {
        // Implementation: trim → lowercase → replaceAll("[^a-z0-9_]", "_")
        Feature f = Feature.builder().code("  My Feature!  ").name("X").build();
        when(featureRepo.findByCode("my_feature_")).thenReturn(Optional.empty());
        when(featureRepo.save(any(Feature.class))).thenAnswer(inv -> inv.getArgument(0));

        Feature created = service.create(f);

        assertThat(created.getCode()).isEqualTo("my_feature_");
    }

    @Test
    void create_codeAlreadyExists_throws() {
        Feature existing = Feature.builder().code("foo").name("Foo").build();
        Feature dupe = Feature.builder().code("foo").name("Bar").build();
        when(featureRepo.findByCode("foo")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.create(dupe))
                .isInstanceOf(DuplicateResourceException.class);
        verify(featureRepo, never()).save(any());
    }

    @Test
    void create_enabledDefaultsToTrueWhenNull() {
        Feature f = Feature.builder().code("foo").name("Foo").build();
        when(featureRepo.findByCode("foo")).thenReturn(Optional.empty());
        when(featureRepo.save(any(Feature.class))).thenAnswer(inv -> inv.getArgument(0));

        Feature created = service.create(f);

        assertThat(created.getEnabled()).isTrue();
    }

    @Test
    void create_enabledFalseRespected() {
        Feature f = Feature.builder().code("foo").name("Foo").enabled(false).build();
        when(featureRepo.findByCode("foo")).thenReturn(Optional.empty());
        when(featureRepo.save(any(Feature.class))).thenAnswer(inv -> inv.getArgument(0));

        Feature created = service.create(f);

        assertThat(created.getEnabled()).isFalse();
    }

    // ── Update ─────────────────────────────────────────────────────────

    @Test
    void update_unknownId_throws() {
        UUID id = UUID.randomUUID();
        when(featureRepo.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(id.toString(), new Feature()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void update_partialPatch_onlyAppliesNonNullFields() {
        UUID id = UUID.randomUUID();
        Feature existing = Feature.builder()
                .id(id).code("foo").name("Old").description("Old desc")
                .category("admin").icon("📦").enabled(true).build();
        when(featureRepo.findById(id)).thenReturn(Optional.of(existing));
        when(featureRepo.save(any(Feature.class))).thenAnswer(inv -> inv.getArgument(0));

        Feature patch = new Feature();
        patch.setName("New Name");
        // Other fields left null.

        Feature updated = service.update(id.toString(), patch);

        assertThat(updated.getName()).isEqualTo("New Name");
        assertThat(updated.getDescription()).isEqualTo("Old desc"); // unchanged
        assertThat(updated.getCategory()).isEqualTo("admin");        // unchanged
        assertThat(updated.getIcon()).isEqualTo("📦");                // unchanged
        assertThat(updated.getEnabled()).isTrue();                    // unchanged
    }

    // ── Delete ─────────────────────────────────────────────────────────

    @Test
    void delete_unknownId_throws() {
        UUID id = UUID.randomUUID();
        when(featureRepo.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(id.toString()))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(featureRepo, never()).delete(any());
    }

    @Test
    void delete_strippsFeatureFromRoleMaps_thenDeletesFeature() {
        UUID id = UUID.randomUUID();
        Feature f = Feature.builder().id(id).code("removed").name("Removed").build();
        RoleFeatureMap m1 = RoleFeatureMap.builder()
                .role("ADMIN").featureCodes(new HashSet<>(Set.of("removed", "kept"))).build();
        RoleFeatureMap m2 = RoleFeatureMap.builder()
                .role("MEMBER").featureCodes(new HashSet<>(Set.of("kept"))).build();

        when(featureRepo.findById(id)).thenReturn(Optional.of(f));
        when(mapRepo.findAll()).thenReturn(List.of(m1, m2));

        service.delete(id.toString());

        // m1 had the code → gets re-saved without it. m2 didn't → not re-saved.
        assertThat(m1.getFeatureCodes()).containsExactly("kept");
        verify(mapRepo, times(1)).save(m1);
        verify(mapRepo, never()).save(m2);
        verify(featureRepo).delete(f);
    }

    // ── Role-Feature matrix ────────────────────────────────────────────

    @Test
    void getMatrix_returnsRoleToFeatureCodes() {
        when(mapRepo.findAll()).thenReturn(List.of(
                RoleFeatureMap.builder().role("ADMIN").featureCodes(Set.of("a", "b")).build(),
                RoleFeatureMap.builder().role("MEMBER").featureCodes(Set.of("c")).build()
        ));

        Map<String, Set<String>> matrix = service.getMatrix();

        assertThat(matrix)
                .containsEntry("ADMIN", Set.of("a", "b"))
                .containsEntry("MEMBER", Set.of("c"));
    }

    @Test
    void setRoleFeatures_existingRole_overwrites() {
        RoleFeatureMap existing = RoleFeatureMap.builder()
                .role("ADMIN").featureCodes(new HashSet<>(Set.of("old"))).build();
        when(mapRepo.findByRole("ADMIN")).thenReturn(Optional.of(existing));

        service.setRoleFeatures("ADMIN", Set.of("new1", "new2"));

        assertThat(existing.getFeatureCodes()).containsExactlyInAnyOrder("new1", "new2");
        verify(mapRepo).save(existing);
    }

    @Test
    void setRoleFeatures_newRole_createsEntry() {
        when(mapRepo.findByRole("MOD")).thenReturn(Optional.empty());

        service.setRoleFeatures("MOD", Set.of("a"));

        var captor = forClass(RoleFeatureMap.class);
        verify(mapRepo).save(captor.capture());
        RoleFeatureMap saved = captor.getValue();
        assertThat(saved.getRole()).isEqualTo("MOD");
        assertThat(saved.getFeatureCodes()).containsExactly("a");
    }

    @Test
    void setRoleFeatures_nullCodes_treatedAsEmpty() {
        when(mapRepo.findByRole("MOD")).thenReturn(Optional.empty());

        service.setRoleFeatures("MOD", null);

        var captor = forClass(RoleFeatureMap.class);
        verify(mapRepo).save(captor.capture());
        assertThat(captor.getValue().getFeatureCodes()).isEmpty();
    }

    // ── getFeaturesForRoles ────────────────────────────────────────────

    @Test
    void getFeaturesForRoles_nullRoles_returnsEmpty() {
        assertThat(service.getFeaturesForRoles(null)).isEmpty();
        verify(mapRepo, never()).findByRoleIn(any());
    }

    @Test
    void getFeaturesForRoles_emptyRoles_returnsEmpty() {
        assertThat(service.getFeaturesForRoles(List.of())).isEmpty();
        verify(mapRepo, never()).findByRoleIn(any());
    }

    @Test
    void getFeaturesForRoles_filtersDisabledAndSortsByCategoryThenName() {
        when(mapRepo.findByRoleIn(List.of("ADMIN"))).thenReturn(List.of(
                RoleFeatureMap.builder().role("ADMIN")
                        .featureCodes(Set.of("a", "b", "c", "d")).build()
        ));
        Feature aDisabled = Feature.builder().code("a").name("Apple").category("food").enabled(false).build();
        Feature bEnabled = Feature.builder().code("b").name("Banana").category("food").enabled(true).build();
        Feature cEnabled = Feature.builder().code("c").name("Aardvark").category("animals").enabled(true).build();
        Feature dEnabled = Feature.builder().code("d").name("Zebra").category("animals").enabled(true).build();
        when(featureRepo.findByCodeIn(Set.of("a", "b", "c", "d"))).thenReturn(List.of(aDisabled, bEnabled, cEnabled, dEnabled));

        List<Feature> features = service.getFeaturesForRoles(List.of("ADMIN"));

        // Disabled "a" filtered out. Sorted by category (animals before food)
        // then name (Aardvark before Zebra within animals).
        assertThat(features)
                .extracting(Feature::getCode)
                .containsExactly("c", "d", "b");
    }

    @Test
    void getFeaturesForRoles_noMappedFeatures_returnsEmpty() {
        when(mapRepo.findByRoleIn(List.of("GUEST"))).thenReturn(List.of(
                RoleFeatureMap.builder().role("GUEST").featureCodes(Set.of()).build()
        ));

        assertThat(service.getFeaturesForRoles(List.of("GUEST"))).isEmpty();
        // Short-circuit: featureRepo.findByCodeIn is not called when codes set is empty.
        verify(featureRepo, never()).findByCodeIn(any());
    }

    // ── listAll ────────────────────────────────────────────────────────

    @Test
    void listAll_delegatesToRepo() {
        Feature a = Feature.builder().code("a").name("A").build();
        when(featureRepo.findAllByOrderByCategoryAscNameAsc()).thenReturn(List.of(a));

        assertThat(service.listAll()).containsExactly(a);
    }
}
