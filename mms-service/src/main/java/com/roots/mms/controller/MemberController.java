package com.roots.mms.controller;

import com.roots.mms.dto.request.CreateMemberRequest;
import com.roots.mms.dto.request.UpdateMemberRequest;
import com.roots.mms.dto.response.MemberResponse;
import com.roots.mms.dto.response.MessageResponse;
import com.roots.mms.entity.MembershipStatus;
import com.roots.mms.entity.MembershipType;
import com.roots.mms.exception.AuthorizationException;
import com.roots.mms.security.SecurityUtils;
import com.roots.mms.service.MemberService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

@RestController
@Slf4j
@RequiredArgsConstructor
@RequestMapping("/api/members")
public class MemberController {

    private final MemberService memberService;

    @PostMapping
    @PreAuthorize("hasRole('ADMIN') or hasRole('MODERATOR') or hasRole('MANAGER')")
    public ResponseEntity<MemberResponse> createMember(@Valid @RequestBody CreateMemberRequest request) {
        log.info("[Member] Create request userId={} type={} by principalId={}", request.getUserId(), request.getMembershipType(), SecurityUtils.getCurrentUserIdOrNull());
        MemberResponse member = memberService.createMember(request);
        return ResponseEntity.ok(member);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('MEMBER') or hasRole('MODERATOR') or hasRole('ADMIN') or hasRole('MANAGER')")
    public ResponseEntity<MemberResponse> getMemberById(@PathVariable String id) {
        log.debug("[Member] Get by id={} requested by principalId={}", id, SecurityUtils.getCurrentUserIdOrNull());
        Optional<MemberResponse> member = memberService.getMemberById(id);
        if (member.isEmpty()) return ResponseEntity.notFound().build();

        boolean isPrivileged = SecurityUtils.getCurrentUserPrincipal()
                .map(p -> p.getAuthorities().stream().anyMatch(a ->
                        a.getAuthority().equals("ROLE_ADMIN") ||
                                a.getAuthority().equals("ROLE_MODERATOR") ||
                                a.getAuthority().equals("ROLE_MANAGER")))
                .orElse(false);

        if (!isPrivileged) {
            String currentUserId = SecurityUtils.getCurrentUserIdOrNull();
            if (currentUserId == null || !member.get().getUser().getId().equals(currentUserId)) {
                throw new AuthorizationException("Member", "read");
            }
        }

        return ResponseEntity.ok(member.get());
    }

    @GetMapping("/membership/{membershipId}")
    @PreAuthorize("hasRole('MEMBER') or hasRole('MODERATOR') or hasRole('ADMIN') or hasRole('MANAGER')")
    public ResponseEntity<MemberResponse> getMemberByMembershipId(@PathVariable String membershipId) {
        log.debug("[Member] Get by membershipId={} requested by principalId={}", membershipId, SecurityUtils.getCurrentUserIdOrNull());
        Optional<MemberResponse> member = memberService.getMemberByMembershipId(membershipId);
        if (member.isEmpty()) return ResponseEntity.notFound().build();

        boolean isPrivileged = SecurityUtils.getCurrentUserPrincipal()
                .map(p -> p.getAuthorities().stream().anyMatch(a ->
                        a.getAuthority().equals("ROLE_ADMIN") ||
                                a.getAuthority().equals("ROLE_MODERATOR") ||
                                a.getAuthority().equals("ROLE_MANAGER")))
                .orElse(false);

        if (!isPrivileged) {
            String currentUserId = SecurityUtils.getCurrentUserIdOrNull();
            if (currentUserId == null || !member.get().getUser().getId().equals(currentUserId)) {
                throw new AuthorizationException("Member", "read");
            }
        }

        return ResponseEntity.ok(member.get());
    }

    @GetMapping("/user/{userId}")
    @PreAuthorize("hasRole('MEMBER') or hasRole('MODERATOR') or hasRole('ADMIN') or hasRole('MANAGER')")
    public ResponseEntity<MemberResponse> getMemberByUserId(@PathVariable String userId) {
        log.debug("[Member] Get by userId={} requested by principalId={}", userId, SecurityUtils.getCurrentUserIdOrNull());
        Optional<MemberResponse> member = memberService.getMemberByUserId(userId);
        if (member.isEmpty()) return ResponseEntity.notFound().build();

        boolean isPrivileged = SecurityUtils.getCurrentUserPrincipal()
                .map(p -> p.getAuthorities().stream().anyMatch(a ->
                        a.getAuthority().equals("ROLE_ADMIN") ||
                                a.getAuthority().equals("ROLE_MODERATOR") ||
                                a.getAuthority().equals("ROLE_MANAGER")))
                .orElse(false);

        if (!isPrivileged) {
            String currentUserId = SecurityUtils.getCurrentUserIdOrNull();
            if (!userId.equals(currentUserId)) {
                throw new AuthorizationException("Member", "read");
            }
        }

        return ResponseEntity.ok(member.get());
    }

    @GetMapping
    @PreAuthorize("hasRole('MODERATOR') or hasRole('ADMIN') or hasRole('MANAGER')")
    public ResponseEntity<Page<MemberResponse>> getAllMembers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir) {
        log.debug("[Member] List page={} size={} sortBy={} sortDir={} by principalId={}", page, size, sortBy, sortDir, SecurityUtils.getCurrentUserIdOrNull());
        Page<MemberResponse> members = memberService.getAllMembers(page, size, sortBy, sortDir);
        return ResponseEntity.ok(members);
    }

    @GetMapping("/status/{status}")
    @PreAuthorize("hasRole('MODERATOR') or hasRole('ADMIN') or hasRole('MANAGER')")
    public ResponseEntity<Page<MemberResponse>> getMembersByStatus(
            @PathVariable MembershipStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        log.debug("[Member] List by status={} page={} size={} by principalId={}", status, page, size, SecurityUtils.getCurrentUserIdOrNull());
        Page<MemberResponse> members = memberService.getMembersByStatus(status, page, size);
        return ResponseEntity.ok(members);
    }

    @GetMapping("/type/{type}")
    @PreAuthorize("hasRole('MODERATOR') or hasRole('ADMIN') or hasRole('MANAGER')")
    public ResponseEntity<Page<MemberResponse>> getMembersByType(
            @PathVariable MembershipType type,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        log.debug("[Member] List by type={} page={} size={} by principalId={}", type, page, size, SecurityUtils.getCurrentUserIdOrNull());
        Page<MemberResponse> members = memberService.getMembersByType(type, page, size);
        return ResponseEntity.ok(members);
    }

    @GetMapping("/search")
    @PreAuthorize("hasRole('MODERATOR') or hasRole('ADMIN') or hasRole('MANAGER')")
    public ResponseEntity<Page<MemberResponse>> searchMembers(
            @RequestParam String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        log.debug("[Member] Search keyword='{}' page={} size={} by principalId={}", keyword, page, size, SecurityUtils.getCurrentUserIdOrNull());
        Page<MemberResponse> members = memberService.searchMembers(keyword, page, size);
        return ResponseEntity.ok(members);
    }

    @PutMapping("/me/tier")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<MemberResponse> selectMyTier(@RequestBody java.util.Map<String, String> body) {
        String userId = SecurityUtils.getCurrentUserIdOrNull();
        if (userId == null) throw new AuthorizationException("Member", "write");
        String categoryCode = body.get("categoryCode");
        String tierCode = body.get("tierCode");
        if (categoryCode == null || tierCode == null) {
            throw new com.roots.mms.exception.BusinessRuleException("categoryCode and tierCode are required");
        }
        log.info("[Member] Self-select tier userId={} category={} tier={}", userId, categoryCode, tierCode);
        return ResponseEntity.ok(memberService.selectTierForUser(userId, categoryCode, tierCode));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('MODERATOR') or hasRole('MANAGER')")
    public ResponseEntity<MemberResponse> updateMember(@PathVariable String id,
                                                       @Valid @RequestBody UpdateMemberRequest request) {
        log.info("[Member] Update id={} by principalId={}", id, SecurityUtils.getCurrentUserIdOrNull());
        MemberResponse member = memberService.updateMember(id, request);
        return ResponseEntity.ok(member);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<MessageResponse> deleteMember(@PathVariable String id) {
        log.warn("[Member] Delete id={} by principalId={}", id, SecurityUtils.getCurrentUserIdOrNull());
        memberService.deleteMember(id);
        return ResponseEntity.ok(new MessageResponse("Member deleted successfully!"));
    }

    @PutMapping("/{id}/deactivate")
    @PreAuthorize("hasRole('ADMIN') or hasRole('MODERATOR') or hasRole('MANAGER')")
    public ResponseEntity<MessageResponse> deactivateMember(@PathVariable String id) {
        log.info("[Member] Deactivate id={} by principalId={}", id, SecurityUtils.getCurrentUserIdOrNull());
        memberService.deactivateMember(id);
        return ResponseEntity.ok(new MessageResponse("Member deactivated successfully!"));
    }

    @PutMapping("/{id}/activate")
    @PreAuthorize("hasRole('ADMIN') or hasRole('MODERATOR') or hasRole('MANAGER')")
    public ResponseEntity<MessageResponse> activateMember(@PathVariable String id) {
        log.info("[Member] Activate id={} by principalId={}", id, SecurityUtils.getCurrentUserIdOrNull());
        memberService.activateMember(id);
        return ResponseEntity.ok(new MessageResponse("Member activated successfully!"));
    }

    @GetMapping("/stats/active-count")
    @PreAuthorize("hasRole('MODERATOR') or hasRole('ADMIN')")
    public ResponseEntity<Long> getActiveMembers() {
        log.debug("[Member] Stats active-count requested by principalId={}", SecurityUtils.getCurrentUserIdOrNull());
        Long count = memberService.getTotalActiveMembers();
        return ResponseEntity.ok(count);
    }
}
