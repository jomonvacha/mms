package com.roots.mms.service;

import com.roots.mms.entity.AppSetting;
import com.roots.mms.exception.ResourceNotFoundException;
import com.roots.mms.repository.AppSettingRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Platform-wide admin-controlled settings. Values are stored as strings; callers
 * use the typed getters. On first startup, defaults are seeded so the admin UI
 * has something to show and runtime reads never fall through to a missing key.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AppSettingService {

    public static final String SIGNUP_INVITE_REQUIRED = "signup.inviteRequired";

    private final AppSettingRepository repository;

    @PostConstruct
    public void seedDefaults() {
        seedIfAbsent(SIGNUP_INVITE_REQUIRED, "true",
                "Require a valid invite code during IDFY signup. Turn off to allow open registration.");
    }

    private void seedIfAbsent(String key, String value, String description) {
        if (repository.existsByKey(key)) return;
        repository.save(AppSetting.builder()
                .key(key)
                .value(value)
                .description(description)
                .updatedBy("system")
                .updatedAt(Instant.now())
                .build());
        log.info("Seeded app setting {}={}", key, value);
    }

    public List<AppSetting> listAll() {
        return repository.findAll();
    }

    public Optional<AppSetting> find(String key) {
        return repository.findByKey(key);
    }

    public boolean getBoolean(String key, boolean fallback) {
        return repository.findByKey(key)
                .map(AppSetting::getValue)
                .map(v -> "true".equalsIgnoreCase(v) || "1".equals(v) || "yes".equalsIgnoreCase(v))
                .orElse(fallback);
    }

    public AppSetting set(String key, String value, String updatedBy) {
        AppSetting setting = repository.findByKey(key)
                .orElseThrow(() -> new ResourceNotFoundException("AppSetting", "key", key));
        setting.setValue(value);
        setting.setUpdatedBy(updatedBy);
        setting.setUpdatedAt(Instant.now());
        AppSetting saved = repository.save(setting);
        log.info("Updated app setting {}={} by {}", key, value, updatedBy);
        return saved;
    }
}
