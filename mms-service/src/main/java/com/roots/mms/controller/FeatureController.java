package com.roots.mms.controller;

import com.roots.mms.entity.Feature;
import com.roots.mms.security.SecurityUtils;
import com.roots.mms.security.services.UserPrincipal;
import com.roots.mms.service.FeatureService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/features")
@RequiredArgsConstructor
@Slf4j
public class FeatureController {

    private final FeatureService featureService;

    // ── Public: current user's features ─────────────────────────────────

    /** Returns the features accessible to the currently authenticated user. */
    @GetMapping("/mine")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<Feature>> myFeatures() {
        List<String> roles = SecurityUtils.getCurrentUserPrincipal()
                .map(p -> p.getAuthorities().stream()
                        .map(GrantedAuthority::getAuthority)
                        .collect(Collectors.toList()))
                .orElse(List.of());
        return ResponseEntity.ok(featureService.getFeaturesForRoles(roles));
    }

    // ── Admin: feature CRUD ─────────────────────────────────────────────

    @GetMapping
    @PreAuthorize("hasRole('ADMIN') or hasRole('MANAGER')")
    public ResponseEntity<List<Feature>> listAll() {
        return ResponseEntity.ok(featureService.listAll());
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Feature> create(@RequestBody Feature feature) {
        return ResponseEntity.ok(featureService.create(feature));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Feature> update(@PathVariable String id, @RequestBody Feature feature) {
        return ResponseEntity.ok(featureService.update(id, feature));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        featureService.delete(id);
        return ResponseEntity.noContent().build();
    }

    // ── Admin: role-feature matrix ──────────────────────────────────────

    /** Returns the full role→features matrix for the admin UI. */
    @GetMapping("/matrix")
    @PreAuthorize("hasRole('ADMIN') or hasRole('MANAGER')")
    public ResponseEntity<Map<String, Set<String>>> getMatrix() {
        return ResponseEntity.ok(featureService.getMatrix());
    }

    /** Sets the feature codes for a specific role. */
    @PutMapping("/matrix/{role}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> setRoleFeatures(@PathVariable String role,
                                                 @RequestBody Set<String> featureCodes) {
        featureService.setRoleFeatures(role, featureCodes);
        return ResponseEntity.noContent().build();
    }
}
