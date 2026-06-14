package com.roots.mms.repository;

import com.roots.mms.entity.RoleFeatureMap;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RoleFeatureMapRepository extends JpaRepository<RoleFeatureMap, UUID> {
    Optional<RoleFeatureMap> findByRole(String role);
    List<RoleFeatureMap> findByRoleIn(java.util.Collection<String> roles);
}
