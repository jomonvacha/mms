package com.roots.mms.repository;

import com.roots.mms.entity.MembershipCategoryConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MembershipCategoryConfigRepository extends JpaRepository<MembershipCategoryConfig, UUID> {
    Optional<MembershipCategoryConfig> findByCode(String code);
    boolean existsByCode(String code);
    List<MembershipCategoryConfig> findAllByOrderBySortOrderAscCodeAsc();
}
