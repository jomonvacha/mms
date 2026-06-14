package com.roots.mms.repository;

import com.roots.mms.entity.MembershipTierConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MembershipTierConfigRepository extends JpaRepository<MembershipTierConfig, UUID> {
    Optional<MembershipTierConfig> findByCategoryCodeAndTierCode(String categoryCode, String tierCode);
    List<MembershipTierConfig> findByCategoryCodeOrderBySortOrderAscTierCodeAsc(String categoryCode);
    List<MembershipTierConfig> findAllByOrderByCategoryCodeAscSortOrderAscTierCodeAsc();
    boolean existsByCategoryCodeAndTierCode(String categoryCode, String tierCode);
    long countByCategoryCode(String categoryCode);
}
