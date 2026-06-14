package com.roots.mms.service;

import com.roots.mms.entity.EnforcementRule;
import com.roots.mms.exception.ResourceNotFoundException;
import com.roots.mms.repository.EnforcementRuleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EnforcementRuleServiceTest {

    private EnforcementRuleRepository repo;
    private EnforcementRuleService service;

    @BeforeEach
    void setUp() {
        repo = mock(EnforcementRuleRepository.class);
        service = new EnforcementRuleService(repo);
    }

    // ── Seeding ────────────────────────────────────────────────────────

    @Test
    void seedDefaults_alreadySeeded_skips() {
        when(repo.count()).thenReturn(5L);

        service.seedDefaults();

        verify(repo, never()).save(any());
    }

    @Test
    void seedDefaults_emptyRepo_seedsNineRules() {
        when(repo.count()).thenReturn(0L);

        service.seedDefaults();

        // Four SYSTEM + five OPTIONAL = nine inserts.
        verify(repo, times(9)).save(any(EnforcementRule.class));
    }

    // ── Read delegation ────────────────────────────────────────────────

    @Test
    void getAll_delegatesSorted() {
        EnforcementRule r = EnforcementRule.builder().text("t").tier("SYSTEM").build();
        when(repo.findAllByOrderBySortOrderAsc()).thenReturn(List.of(r));

        assertThat(service.getAll()).containsExactly(r);
    }

    @Test
    void getActive_delegates() {
        when(repo.findByActiveTrueOrderBySortOrderAsc()).thenReturn(List.of());

        assertThat(service.getActive()).isEmpty();
        verify(repo, atLeastOnce()).findByActiveTrueOrderBySortOrderAsc();
    }

    @Test
    void getSystemRules_filtersByTierSystem() {
        EnforcementRule r = EnforcementRule.builder().tier("SYSTEM").build();
        when(repo.findByTierAndActiveTrueOrderBySortOrderAsc("SYSTEM")).thenReturn(List.of(r));

        assertThat(service.getSystemRules()).containsExactly(r);
    }

    @Test
    void getOptionalRules_filtersByTierOptional() {
        EnforcementRule r = EnforcementRule.builder().tier("OPTIONAL").build();
        when(repo.findByTierAndActiveTrueOrderBySortOrderAsc("OPTIONAL")).thenReturn(List.of(r));

        assertThat(service.getOptionalRules()).containsExactly(r);
    }

    // ── CRUD ───────────────────────────────────────────────────────────

    @Test
    void create_savesPassThrough() {
        EnforcementRule r = EnforcementRule.builder().text("t").build();
        when(repo.save(r)).thenReturn(r);

        assertThat(service.create(r)).isSameAs(r);
    }

    @Test
    void update_unknownId_throws() {
        UUID id = UUID.randomUUID();
        when(repo.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(id.toString(), Map.of("text", "x")))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void update_appliesEachKeyExplicitlyPresent() {
        UUID id = UUID.randomUUID();
        EnforcementRule existing = EnforcementRule.builder()
                .id(id).text("old").tier("OPTIONAL").category("style")
                .enabledByDefault(true).sortOrder(5).active(true).build();
        when(repo.findById(id)).thenReturn(Optional.of(existing));
        when(repo.save(any(EnforcementRule.class))).thenAnswer(inv -> inv.getArgument(0));

        Map<String, Object> patch = new HashMap<>();
        patch.put("text", "new text");
        patch.put("tier", "SYSTEM");
        patch.put("category", "security");
        patch.put("enabledByDefault", false);
        patch.put("sortOrder", 99);
        patch.put("active", false);

        EnforcementRule updated = service.update(id.toString(), patch);

        assertThat(updated.getText()).isEqualTo("new text");
        assertThat(updated.getTier()).isEqualTo("SYSTEM");
        assertThat(updated.getCategory()).isEqualTo("security");
        assertThat(updated.getEnabledByDefault()).isFalse();
        assertThat(updated.getSortOrder()).isEqualTo(99);
        assertThat(updated.getActive()).isFalse();
    }

    @Test
    void update_partialPatch_leavesOthersUntouched() {
        UUID id = UUID.randomUUID();
        EnforcementRule existing = EnforcementRule.builder()
                .id(id).text("old").tier("SYSTEM").category("security")
                .enabledByDefault(true).sortOrder(1).active(true).build();
        when(repo.findById(id)).thenReturn(Optional.of(existing));
        when(repo.save(any(EnforcementRule.class))).thenAnswer(inv -> inv.getArgument(0));

        EnforcementRule updated = service.update(id.toString(), Map.of("text", "new"));

        assertThat(updated.getText()).isEqualTo("new");
        assertThat(updated.getTier()).isEqualTo("SYSTEM");
        assertThat(updated.getCategory()).isEqualTo("security");
        assertThat(updated.getEnabledByDefault()).isTrue();
        assertThat(updated.getSortOrder()).isEqualTo(1);
        assertThat(updated.getActive()).isTrue();
    }

    @Test
    void update_explicitNullValueIsApplied() {
        // Map.containsKey is what guards each setter, not null checks. Passing
        // a HashMap with a null value should overwrite the field with null —
        // this matters because Map.of(k, null) throws.
        UUID id = UUID.randomUUID();
        EnforcementRule existing = EnforcementRule.builder()
                .id(id).text("old").tier("OPTIONAL").category("style").build();
        when(repo.findById(id)).thenReturn(Optional.of(existing));
        when(repo.save(any(EnforcementRule.class))).thenAnswer(inv -> inv.getArgument(0));

        Map<String, Object> patch = new HashMap<>();
        patch.put("category", null);

        EnforcementRule updated = service.update(id.toString(), patch);

        assertThat(updated.getCategory()).isNull();
    }

    @Test
    void delete_unknownId_throws() {
        UUID id = UUID.randomUUID();
        when(repo.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(id.toString()))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(repo, never()).delete(any());
    }

    @Test
    void delete_existing_callsRepoDelete() {
        UUID id = UUID.randomUUID();
        EnforcementRule r = EnforcementRule.builder().id(id).text("t").build();
        when(repo.findById(id)).thenReturn(Optional.of(r));

        service.delete(id.toString());

        verify(repo).delete(r);
    }
}
