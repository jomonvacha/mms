package com.roots.mms.service;

import com.roots.mms.entity.Feature;
import com.roots.mms.entity.RoleFeatureMap;
import com.roots.mms.exception.BusinessRuleException;
import com.roots.mms.exception.DuplicateResourceException;
import com.roots.mms.exception.ResourceNotFoundException;
import com.roots.mms.repository.FeatureRepository;
import com.roots.mms.repository.RoleFeatureMapRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class FeatureService {

    private final FeatureRepository featureRepository;
    private final RoleFeatureMapRepository roleFeatureMapRepository;

    // ── Feature CRUD ────────────────────────────────────────────────────

    public List<Feature> listAll() {
        return featureRepository.findAllByOrderByCategoryAscNameAsc();
    }

    public Feature create(Feature feature) {
        if (feature.getCode() == null || feature.getCode().isBlank()) {
            throw new BusinessRuleException("Feature code is required");
        }
        feature.setCode(feature.getCode().trim().toLowerCase().replaceAll("[^a-z0-9_]", "_"));
        if (featureRepository.findByCode(feature.getCode()).isPresent()) {
            throw new DuplicateResourceException("Feature", "code", feature.getCode());
        }
        if (feature.getEnabled() == null) feature.setEnabled(true);
        return featureRepository.save(feature);
    }

    public Feature update(String id, Feature patch) {
        Feature existing = featureRepository.findById(java.util.UUID.fromString(id))
                .orElseThrow(() -> new ResourceNotFoundException("Feature", "id", id));
        if (patch.getName() != null) existing.setName(patch.getName());
        if (patch.getDescription() != null) existing.setDescription(patch.getDescription());
        if (patch.getCategory() != null) existing.setCategory(patch.getCategory());
        if (patch.getIcon() != null) existing.setIcon(patch.getIcon());
        if (patch.getEnabled() != null) existing.setEnabled(patch.getEnabled());
        return featureRepository.save(existing);
    }

    public void delete(String id) {
        Feature f = featureRepository.findById(java.util.UUID.fromString(id))
                .orElseThrow(() -> new ResourceNotFoundException("Feature", "id", id));
        // Remove from all role mappings
        List<RoleFeatureMap> maps = roleFeatureMapRepository.findAll();
        for (RoleFeatureMap m : maps) {
            if (m.getFeatureCodes().remove(f.getCode())) {
                roleFeatureMapRepository.save(m);
            }
        }
        featureRepository.delete(f);
    }

    // ── Role-Feature mapping ────────────────────────────────────────────

    /** Returns the full matrix: every role → its feature codes. */
    public Map<String, Set<String>> getMatrix() {
        Map<String, Set<String>> matrix = new LinkedHashMap<>();
        for (RoleFeatureMap m : roleFeatureMapRepository.findAll()) {
            matrix.put(m.getRole(), m.getFeatureCodes());
        }
        return matrix;
    }

    /** Sets the feature codes for a specific role (replaces). */
    public void setRoleFeatures(String role, Set<String> featureCodes) {
        RoleFeatureMap m = roleFeatureMapRepository.findByRole(role)
                .orElseGet(() -> RoleFeatureMap.builder().role(role).featureCodes(new HashSet<>()).build());
        m.setFeatureCodes(featureCodes != null ? featureCodes : new HashSet<>());
        roleFeatureMapRepository.save(m);
        log.info("Updated features for role {}: {}", role, featureCodes);
    }

    /** Returns the enabled features accessible to a user with the given roles. */
    public List<Feature> getFeaturesForRoles(Collection<String> roles) {
        if (roles == null || roles.isEmpty()) return List.of();
        List<RoleFeatureMap> maps = roleFeatureMapRepository.findByRoleIn(roles);
        Set<String> codes = maps.stream()
                .flatMap(m -> m.getFeatureCodes().stream())
                .collect(Collectors.toSet());
        if (codes.isEmpty()) return List.of();
        return featureRepository.findByCodeIn(codes).stream()
                .filter(f -> Boolean.TRUE.equals(f.getEnabled()))
                .sorted(Comparator.comparing((Feature f) -> f.getCategory() != null ? f.getCategory() : "")
                        .thenComparing(f -> f.getName() != null ? f.getName() : ""))
                .toList();
    }
}
