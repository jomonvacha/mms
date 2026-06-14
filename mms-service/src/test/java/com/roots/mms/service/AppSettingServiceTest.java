package com.roots.mms.service;

import com.roots.mms.entity.AppSetting;
import com.roots.mms.exception.ResourceNotFoundException;
import com.roots.mms.repository.AppSettingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AppSettingServiceTest {

    private AppSettingRepository repo;
    private AppSettingService service;

    @BeforeEach
    void setUp() {
        repo = mock(AppSettingRepository.class);
        service = new AppSettingService(repo);
    }

    // ── Seeding ────────────────────────────────────────────────────────

    @Test
    void seedDefaults_keyAlreadyExists_skipsSave() {
        when(repo.existsByKey(AppSettingService.SIGNUP_INVITE_REQUIRED)).thenReturn(true);

        service.seedDefaults();

        verify(repo, never()).save(any());
    }

    @Test
    void seedDefaults_keyAbsent_seedsExpectedDefault() {
        when(repo.existsByKey(AppSettingService.SIGNUP_INVITE_REQUIRED)).thenReturn(false);

        service.seedDefaults();

        var captor = forClass(AppSetting.class);
        verify(repo, atLeastOnce()).save(captor.capture());
        AppSetting seeded = captor.getValue();
        assertThat(seeded.getKey()).isEqualTo(AppSettingService.SIGNUP_INVITE_REQUIRED);
        assertThat(seeded.getValue()).isEqualTo("true");
        assertThat(seeded.getUpdatedBy()).isEqualTo("system");
        assertThat(seeded.getDescription()).contains("invite code");
        assertThat(seeded.getUpdatedAt()).isNotNull();
    }

    // ── Reads ──────────────────────────────────────────────────────────

    @Test
    void listAll_delegatesToRepo() {
        AppSetting a = AppSetting.builder().key("k").value("v").build();
        when(repo.findAll()).thenReturn(List.of(a));

        assertThat(service.listAll()).containsExactly(a);
    }

    @Test
    void find_delegatesToRepo() {
        AppSetting a = AppSetting.builder().key("k").value("v").build();
        when(repo.findByKey("k")).thenReturn(Optional.of(a));

        assertThat(service.find("k")).contains(a);
    }

    // ── getBoolean ─────────────────────────────────────────────────────

    @Test
    void getBoolean_missingKey_returnsFallback() {
        when(repo.findByKey("missing")).thenReturn(Optional.empty());

        assertThat(service.getBoolean("missing", true)).isTrue();
        assertThat(service.getBoolean("missing", false)).isFalse();
    }

    @Test
    void getBoolean_acceptsTrueVariants() {
        for (String trueValue : List.of("true", "TRUE", "True", "1", "yes", "YES", "Yes")) {
            when(repo.findByKey("k")).thenReturn(Optional.of(AppSetting.builder().key("k").value(trueValue).build()));
            assertThat(service.getBoolean("k", false))
                    .as("value=%s should map to true", trueValue)
                    .isTrue();
        }
    }

    @Test
    void getBoolean_anythingElseIsFalse() {
        // Note: when a key exists with a non-truthy value, the fallback is NOT
        // applied — the caller wanted the configured value. Only "true" / "1" /
        // "yes" (case-insensitive for true / yes) map to true; everything else
        // (including "false", "no", "0", arbitrary strings, blank) is false.
        for (String falseValue : List.of("false", "no", "0", "", "anything", " ")) {
            when(repo.findByKey("k")).thenReturn(Optional.of(AppSetting.builder().key("k").value(falseValue).build()));
            assertThat(service.getBoolean("k", true))
                    .as("value=%s with fallback=true should still resolve to false", falseValue)
                    .isFalse();
        }
    }

    // ── set ────────────────────────────────────────────────────────────

    @Test
    void set_unknownKey_throws() {
        when(repo.findByKey("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.set("missing", "value", "admin"))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(repo, never()).save(any());
    }

    @Test
    void set_existingKey_updatesValueAndMetadata() {
        AppSetting existing = AppSetting.builder()
                .key("k").value("old").updatedBy("system").build();
        when(repo.findByKey("k")).thenReturn(Optional.of(existing));
        when(repo.save(any(AppSetting.class))).thenAnswer(inv -> inv.getArgument(0));

        AppSetting saved = service.set("k", "new", "admin@example.com");

        assertThat(saved.getValue()).isEqualTo("new");
        assertThat(saved.getUpdatedBy()).isEqualTo("admin@example.com");
        assertThat(saved.getUpdatedAt()).isNotNull();
        verify(repo).save(existing);
    }
}
