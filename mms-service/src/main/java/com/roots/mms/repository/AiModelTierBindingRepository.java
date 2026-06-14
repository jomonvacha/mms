package com.roots.mms.repository;

import com.roots.mms.entity.AiModelTierBinding;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AiModelTierBindingRepository extends JpaRepository<AiModelTierBinding, UUID> {

    List<AiModelTierBinding> findByModelCode(String modelCode);

    List<AiModelTierBinding> findByCategoryCodeAndTierCode(String categoryCode, String tierCode);

    Optional<AiModelTierBinding> findByModelCodeAndCategoryCodeAndTierCode(
            String modelCode, String categoryCode, String tierCode);

    /** Used to enforce "exactly one default per tier" — service layer clears siblings before setting a new default. */
    List<AiModelTierBinding> findByCategoryCodeAndTierCodeAndIsDefault(
            String categoryCode, String tierCode, Boolean isDefault);

    void deleteByModelCode(String modelCode);
}
