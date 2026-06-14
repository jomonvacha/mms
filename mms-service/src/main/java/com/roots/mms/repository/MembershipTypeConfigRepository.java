package com.roots.mms.repository;

import com.roots.mms.entity.MembershipTypeConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface MembershipTypeConfigRepository extends JpaRepository<MembershipTypeConfig, UUID> {
    Optional<MembershipTypeConfig> findByCode(String code);
}
