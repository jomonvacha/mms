package com.roots.mms.controller;

import com.roots.mms.entity.Role;
import com.roots.mms.exception.BusinessRuleException;
import com.roots.mms.exception.DuplicateResourceException;
import com.roots.mms.exception.ResourceNotFoundException;
import com.roots.mms.repository.RoleFeatureMapRepository;
import com.roots.mms.repository.RoleRepository;
import com.roots.mms.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@Slf4j
@RequiredArgsConstructor
@RequestMapping("/api/roles")
public class RoleController {

    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final RoleFeatureMapRepository roleFeatureMapRepository;

    /** Returns available membership types. */
    @GetMapping("/membership-types")
    @PreAuthorize("hasRole('ADMIN') or hasRole('MODERATOR') or hasRole('MANAGER')")
    public ResponseEntity<List<String>> membershipTypes() {
        return ResponseEntity.ok(
            java.util.Arrays.stream(com.roots.mms.entity.MembershipType.values())
                .map(Enum::name)
                .toList()
        );
    }

    /** List all roles (admin/manager/moderator can view). */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN') or hasRole('MODERATOR') or hasRole('MANAGER')")
    public ResponseEntity<List<Role>> listRoles() {
        return ResponseEntity.ok(roleRepository.findAll());
    }

    /** Create a custom role. Name is auto-prefixed with ROLE_ if not already. */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Role> createRole(@RequestBody Map<String, String> body) {
        String rawName = body.getOrDefault("name", "").trim().toUpperCase().replaceAll("[^A-Z0-9_]", "_");
        if (rawName.isBlank()) throw new BusinessRuleException("Role name is required.");
        String name = rawName.startsWith("ROLE_") ? rawName : "ROLE_" + rawName;

        if (roleRepository.findByName(name).isPresent()) {
            throw new DuplicateResourceException("Role", "name", name);
        }

        Role role = Role.builder()
                .name(name)
                .displayName(body.getOrDefault("displayName", name.replace("ROLE_", "").replace("_", " ")))
                .description(body.getOrDefault("description", ""))
                .category(body.getOrDefault("category", "System"))
                .system(false)
                .build();
        Role saved = roleRepository.save(role);
        log.info("Created custom role: {}", name);
        return ResponseEntity.ok(saved);
    }

    /** Update a role's display name and description. */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Role> updateRole(@PathVariable String id, @RequestBody Map<String, String> body) {
        Role role = roleRepository.findById(java.util.UUID.fromString(id))
                .orElseThrow(() -> new ResourceNotFoundException("Role", "id", id));
        if (body.containsKey("displayName")) role.setDisplayName(body.get("displayName"));
        if (body.containsKey("description")) role.setDescription(body.get("description"));
        if (body.containsKey("category")) role.setCategory(body.get("category"));
        return ResponseEntity.ok(roleRepository.save(role));
    }

    /**
     * Delete a custom role. Blocked when:
     * - It's a system role (cannot be deleted)
     * - Users are still assigned to it
     * - Features are still mapped to it
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteRole(@PathVariable String id) {
        Role role = roleRepository.findById(java.util.UUID.fromString(id))
                .orElseThrow(() -> new ResourceNotFoundException("Role", "id", id));
        if (Boolean.TRUE.equals(role.getSystem())) {
            throw new BusinessRuleException("This role cannot be deleted while it has active assignments.");
        }
        long userCount = userRepository.findAll().stream()
                .filter(u -> u.getRoles().stream().anyMatch(r -> r.getName().equals(role.getName())))
                .count();
        if (userCount > 0) {
            throw new BusinessRuleException("This role cannot be deleted while it has active assignments.");
        }
        var featureMap = roleFeatureMapRepository.findByRole(role.getName());
        if (featureMap.isPresent() && !featureMap.get().getFeatureCodes().isEmpty()) {
            int featureCount = featureMap.get().getFeatureCodes().size();
            throw new BusinessRuleException("This role cannot be deleted while it has active assignments.");
        }
        // Clean up empty feature mapping if exists
        featureMap.ifPresent(roleFeatureMapRepository::delete);
        roleRepository.delete(role);
        log.info("Deleted custom role: {}", role.getName());
        return ResponseEntity.noContent().build();
    }
}
