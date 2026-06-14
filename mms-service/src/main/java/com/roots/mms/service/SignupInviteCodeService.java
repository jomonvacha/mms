package com.roots.mms.service;

import com.roots.mms.entity.SignupInviteCode;
import com.roots.mms.exception.BusinessRuleException;
import com.roots.mms.exception.DuplicateResourceException;
import com.roots.mms.exception.ResourceNotFoundException;
import com.roots.mms.repository.SignupInviteCodeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

/**
 * Manages IDFY signup invite codes. Codes are consumed during the public signup
 * flow and managed by admins.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SignupInviteCodeService {

    private final SignupInviteCodeRepository repository;

    public List<SignupInviteCode> listAll() {
        return repository.findAll();
    }

    public SignupInviteCode create(String code, String description, Integer maxUses, Instant expiresAt, String createdBy) {
        if (code == null || code.isBlank()) throw new BusinessRuleException("Invite code is required");
        String normalized = code.trim().toUpperCase();
        if (repository.existsByCode(normalized)) {
            throw new DuplicateResourceException("SignupInviteCode", "code", normalized);
        }
        if (maxUses != null && maxUses < 1) {
            throw new BusinessRuleException("maxUses must be at least 1 (or null for unlimited)");
        }
        SignupInviteCode invite = SignupInviteCode.builder()
                .code(normalized)
                .description(description)
                .maxUses(maxUses)
                .usedCount(0)
                .expiresAt(expiresAt)
                .active(true)
                .createdBy(createdBy)
                .createdAt(Instant.now())
                .build();
        return repository.save(invite);
    }

    public SignupInviteCode update(String id, Boolean active, Integer maxUses, Instant expiresAt, String description) {
        SignupInviteCode invite = repository.findById(java.util.UUID.fromString(id))
                .orElseThrow(() -> new ResourceNotFoundException("SignupInviteCode", "id", id));
        if (active != null) invite.setActive(active);
        if (maxUses != null) {
            if (maxUses < 1) throw new BusinessRuleException("maxUses must be at least 1");
            if (invite.getUsedCount() != null && maxUses < invite.getUsedCount()) {
                throw new BusinessRuleException("maxUses cannot be below current usedCount (" + invite.getUsedCount() + ")");
            }
            invite.setMaxUses(maxUses);
        }
        if (expiresAt != null) invite.setExpiresAt(expiresAt);
        if (description != null) invite.setDescription(description);
        return repository.save(invite);
    }

    public void delete(String id) {
        java.util.UUID uuid = java.util.UUID.fromString(id);
        if (!repository.existsById(uuid)) {
            throw new ResourceNotFoundException("SignupInviteCode", "id", id);
        }
        repository.deleteById(uuid);
    }

    /**
     * Returns the invite if the code is presently redeemable. Throws
     * {@link BusinessRuleException} with a user-facing reason otherwise.
     */
    public SignupInviteCode requireRedeemable(String code) {
        if (code == null || code.isBlank()) {
            throw new BusinessRuleException("Invite code is required");
        }
        SignupInviteCode invite = repository.findByCode(code.trim().toUpperCase())
                .orElseThrow(() -> new BusinessRuleException("This invite code is not recognized"));
        if (!Boolean.TRUE.equals(invite.getActive())) {
            throw new BusinessRuleException("This invite code is no longer active");
        }
        if (invite.getExpiresAt() != null && invite.getExpiresAt().isBefore(Instant.now())) {
            throw new BusinessRuleException("This invite code has expired");
        }
        if (invite.getMaxUses() != null) {
            int used = invite.getUsedCount() == null ? 0 : invite.getUsedCount();
            if (used >= invite.getMaxUses()) {
                throw new BusinessRuleException("This invite code has reached its usage limit");
            }
        }
        return invite;
    }

    /**
     * Validates + atomically increments usage counter. Called from the signup flow.
     * On a race at the last slot, the second caller will see the exhausted state on
     * the subsequent read; for stricter guarantees upgrade to a findAndModify-with-$expr.
     */
    public void consume(String code) {
        SignupInviteCode invite = requireRedeemable(code);
        int current = invite.getUsedCount() == null ? 0 : invite.getUsedCount();
        invite.setUsedCount(current + 1);
        repository.save(invite);
        log.info("Consumed invite code {} (usedCount={}{}) ", invite.getCode(), invite.getUsedCount(),
                invite.getMaxUses() != null ? "/" + invite.getMaxUses() : "");
    }
}
