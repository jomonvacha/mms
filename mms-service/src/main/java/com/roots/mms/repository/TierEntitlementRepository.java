package com.roots.mms.repository;

import com.roots.mms.entity.TierEntitlement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TierEntitlementRepository extends JpaRepository<TierEntitlement, UUID> {
    List<TierEntitlement> findByCategoryCodeAndTierCode(String categoryCode, String tierCode);
    Optional<TierEntitlement> findByCategoryCodeAndTierCodeAndEntitlementKey(
            String categoryCode, String tierCode, String entitlementKey);
    List<TierEntitlement> findByEntitlementKey(String entitlementKey);
    void deleteByCategoryCodeAndTierCode(String categoryCode, String tierCode);
    void deleteByEntitlementKey(String entitlementKey);
}
