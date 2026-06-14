package com.roots.mms.service;

import com.roots.mms.entity.LocaleOption;
import com.roots.mms.exception.DuplicateResourceException;
import com.roots.mms.exception.ResourceNotFoundException;
import com.roots.mms.repository.LocaleOptionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pure unit test (no Spring) for LocaleService. Mocks the single repository
 * dependency so the test stays fast and deterministic.
 */
class LocaleServiceTest {

    private LocaleOptionRepository repo;
    private LocaleService service;

    @BeforeEach
    void setUp() {
        repo = mock(LocaleOptionRepository.class);
        service = new LocaleService(repo);
    }

    // ── Seeding ────────────────────────────────────────────────────────

    @Test
    void seedDefaults_whenAlreadySeeded_skipsInserts() {
        when(repo.count()).thenReturn(57L);

        service.seedDefaults();

        verify(repo, never()).save(any());
    }

    @Test
    void seedDefaults_whenEmpty_writesLanguagesCountriesAndTimezones() {
        when(repo.count()).thenReturn(0L);

        service.seedDefaults();

        // Implementation inserts 15 languages + 25 countries + 17 timezones = 57.
        // Asserting the lower bound + per-type presence keeps the test sturdy
        // against future minor list edits without going silent on real regressions.
        verify(repo, atLeast(50)).save(any());
        verify(repo, atLeast(1)).save(argThat(opt -> "LANGUAGE".equals(opt.getType()) && "en".equals(opt.getCode())));
        verify(repo, atLeast(1)).save(argThat(opt -> "COUNTRY".equals(opt.getType()) && "US".equals(opt.getCode())));
        verify(repo, atLeast(1)).save(argThat(opt -> "TIMEZONE".equals(opt.getType()) && "UTC".equals(opt.getCode())));
    }

    // ── Queries ────────────────────────────────────────────────────────

    @Test
    void getEnabledByType_delegatesToRepo() {
        LocaleOption en = LocaleOption.builder().type("LANGUAGE").code("en").label("English").build();
        when(repo.findByTypeAndEnabledTrueOrderBySortOrderAsc("LANGUAGE")).thenReturn(List.of(en));

        List<LocaleOption> result = service.getEnabledByType("LANGUAGE");

        assertThat(result).containsExactly(en);
        verify(repo).findByTypeAndEnabledTrueOrderBySortOrderAsc("LANGUAGE");
    }

    @Test
    void getAllByType_includesDisabledEntries() {
        when(repo.findByTypeOrderBySortOrderAsc("COUNTRY")).thenReturn(List.of());

        List<LocaleOption> result = service.getAllByType("COUNTRY");

        assertThat(result).isEmpty();
        verify(repo).findByTypeOrderBySortOrderAsc("COUNTRY");
    }

    // ── Create ─────────────────────────────────────────────────────────

    @Test
    void create_duplicateTypeAndCode_throws() {
        LocaleOption opt = LocaleOption.builder().type("LANGUAGE").code("en").label("English").build();
        when(repo.existsByTypeAndCode("LANGUAGE", "en")).thenReturn(true);

        assertThatThrownBy(() -> service.create(opt))
                .isInstanceOf(DuplicateResourceException.class);
        verify(repo, never()).save(any());
    }

    @Test
    void create_unique_savesAndReturns() {
        LocaleOption opt = LocaleOption.builder().type("LANGUAGE").code("xx").label("Test").build();
        when(repo.existsByTypeAndCode("LANGUAGE", "xx")).thenReturn(false);
        when(repo.save(opt)).thenReturn(opt);

        LocaleOption saved = service.create(opt);

        assertThat(saved).isSameAs(opt);
        verify(repo).save(opt);
    }

    // ── Update ─────────────────────────────────────────────────────────

    @Test
    void update_unknownId_throws() {
        UUID id = UUID.randomUUID();
        when(repo.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(id.toString(), new LocaleOption()))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(repo, never()).save(any());
    }

    @Test
    void update_partialPatch_appliesOnlyNonNullFields() {
        UUID id = UUID.randomUUID();
        LocaleOption existing = LocaleOption.builder()
                .id(id).type("LANGUAGE").code("en").label("English").sortOrder(0).enabled(true).build();
        when(repo.findById(id)).thenReturn(Optional.of(existing));
        when(repo.save(existing)).thenReturn(existing);

        LocaleOption patch = new LocaleOption();
        patch.setLabel("English (US)");
        // Other fields left null — must remain unchanged.

        LocaleOption result = service.update(id.toString(), patch);

        assertThat(result.getLabel()).isEqualTo("English (US)");
        assertThat(result.getType()).isEqualTo("LANGUAGE"); // unchanged
        assertThat(result.getCode()).isEqualTo("en");       // unchanged
        assertThat(result.getSortOrder()).isEqualTo(0);      // unchanged
        assertThat(result.getEnabled()).isTrue();            // unchanged
    }

    @Test
    void update_disablesEntry() {
        UUID id = UUID.randomUUID();
        LocaleOption existing = LocaleOption.builder()
                .id(id).type("COUNTRY").code("XX").label("Old").enabled(true).build();
        when(repo.findById(id)).thenReturn(Optional.of(existing));
        when(repo.save(any(LocaleOption.class))).thenAnswer(inv -> inv.getArgument(0));

        LocaleOption patch = new LocaleOption();
        patch.setEnabled(false);

        LocaleOption result = service.update(id.toString(), patch);

        assertThat(result.getEnabled()).isFalse();
    }

    // ── Delete ─────────────────────────────────────────────────────────

    @Test
    void delete_unknownId_throws() {
        UUID id = UUID.randomUUID();
        when(repo.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(id.toString()))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(repo, never()).delete(any());
    }

    @Test
    void delete_existing_callsRepo() {
        UUID id = UUID.randomUUID();
        LocaleOption existing = LocaleOption.builder().id(id).build();
        when(repo.findById(id)).thenReturn(Optional.of(existing));

        service.delete(id.toString());

        verify(repo, times(1)).delete(existing);
    }

    // Mockito's argThat helper imported as static for readability.
    private static <T> T argThat(org.mockito.ArgumentMatcher<T> matcher) {
        return org.mockito.ArgumentMatchers.argThat(matcher);
    }
}
