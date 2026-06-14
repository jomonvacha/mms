package com.roots.mms.repository;

import com.roots.mms.entity.EnforcementRule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface EnforcementRuleRepository extends JpaRepository<EnforcementRule, UUID> {
    List<EnforcementRule> findByActiveTrueOrderBySortOrderAsc();
    List<EnforcementRule> findByTierAndActiveTrueOrderBySortOrderAsc(String tier);
    List<EnforcementRule> findAllByOrderBySortOrderAsc();
}
