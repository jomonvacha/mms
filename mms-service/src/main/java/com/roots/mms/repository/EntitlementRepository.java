package com.roots.mms.repository;

import com.roots.mms.entity.Entitlement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface EntitlementRepository extends JpaRepository<Entitlement, UUID> {
    Optional<Entitlement> findByKey(String key);
    boolean existsByKey(String key);
}
