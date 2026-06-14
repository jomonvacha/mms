package com.roots.mms.controller;

import com.roots.mms.entity.MembershipTypeConfig;
import com.roots.mms.exception.BusinessRuleException;
import com.roots.mms.exception.DuplicateResourceException;
import com.roots.mms.exception.ResourceNotFoundException;
import com.roots.mms.repository.MemberRepository;
import com.roots.mms.repository.MembershipTypeConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/membership-types")
@RequiredArgsConstructor
@Slf4j
public class MembershipTypeController {

    private final MembershipTypeConfigRepository repo;
    private final MemberRepository memberRepository;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN') or hasRole('MODERATOR') or hasRole('MANAGER')")
    public ResponseEntity<List<MembershipTypeConfig>> list() {
        return ResponseEntity.ok(repo.findAll());
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<MembershipTypeConfig> create(@RequestBody Map<String, String> body) {
        String code = (body.getOrDefault("code", "")).trim().toUpperCase().replaceAll("[^A-Z0-9_]", "_");
        if (code.isBlank()) throw new BusinessRuleException("Code is required.");
        if (repo.findByCode(code).isPresent()) throw new DuplicateResourceException("MembershipType", "code", code);

        MembershipTypeConfig t = MembershipTypeConfig.builder()
                .code(code)
                .displayName(body.getOrDefault("displayName", code))
                .description(body.getOrDefault("description", ""))
                .system(false)
                .build();
        log.info("Created membership type: {}", code);
        return ResponseEntity.ok(repo.save(t));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<MembershipTypeConfig> update(@PathVariable String id, @RequestBody Map<String, String> body) {
        MembershipTypeConfig t = repo.findById(java.util.UUID.fromString(id)).orElseThrow(() -> new ResourceNotFoundException("MembershipType", "id", id));
        if (body.containsKey("displayName")) t.setDisplayName(body.get("displayName"));
        if (body.containsKey("description")) t.setDescription(body.get("description"));
        return ResponseEntity.ok(repo.save(t));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        MembershipTypeConfig t = repo.findById(java.util.UUID.fromString(id)).orElseThrow(() -> new ResourceNotFoundException("MembershipType", "id", id));
        if (Boolean.TRUE.equals(t.getSystem())) {
            throw new BusinessRuleException("System membership type '" + t.getCode() + "' cannot be deleted.");
        }
        long count = memberRepository.findAll().stream()
                .filter(m -> t.getCode().equals(m.getMembershipType() != null ? m.getMembershipType().name() : null))
                .count();
        if (count > 0) {
            throw new BusinessRuleException("Cannot delete — " + count + " member(s) have this type. Reassign them first.");
        }
        repo.delete(t);
        log.info("Deleted membership type: {}", t.getCode());
        return ResponseEntity.noContent().build();
    }
}
