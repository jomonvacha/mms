package com.roots.mms.repository;

import com.roots.mms.entity.Feature;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FeatureRepository extends JpaRepository<Feature, UUID> {
    Optional<Feature> findByCode(String code);
    boolean existsByCode(String code);
    List<Feature> findByEnabledTrue();
    List<Feature> findByCodeIn(java.util.Collection<String> codes);
    List<Feature> findAllByOrderByCategoryAscNameAsc();
}
