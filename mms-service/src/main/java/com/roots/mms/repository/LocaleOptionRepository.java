package com.roots.mms.repository;

import com.roots.mms.entity.LocaleOption;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LocaleOptionRepository extends JpaRepository<LocaleOption, UUID> {
    List<LocaleOption> findByTypeAndEnabledTrueOrderBySortOrderAsc(String type);
    List<LocaleOption> findByTypeOrderBySortOrderAsc(String type);
    Optional<LocaleOption> findByTypeAndCode(String type, String code);
    boolean existsByTypeAndCode(String type, String code);
}
