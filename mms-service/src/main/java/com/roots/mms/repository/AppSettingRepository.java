package com.roots.mms.repository;

import com.roots.mms.entity.AppSetting;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface AppSettingRepository extends JpaRepository<AppSetting, UUID> {
    Optional<AppSetting> findByKey(String key);
    boolean existsByKey(String key);
}
