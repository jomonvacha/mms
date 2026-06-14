package com.roots.mms.service;

import com.roots.mms.dto.request.CreateMemberRequest;
import com.roots.mms.dto.request.UpdateMemberRequest;
import com.roots.mms.dto.response.MemberResponse;
import com.roots.mms.dto.response.UserResponse;
import com.roots.mms.entity.Member;
import com.roots.mms.entity.MembershipStatus;
import com.roots.mms.entity.MembershipType;
import com.roots.mms.entity.User;
import com.roots.mms.entity.UserSummary;
import com.roots.mms.exception.BusinessRuleException;
import com.roots.mms.exception.DuplicateResourceException;
import com.roots.mms.exception.ResourceNotFoundException;
import com.roots.mms.repository.MemberRepository;
import com.roots.mms.repository.MembershipCategoryConfigRepository;
import com.roots.mms.repository.MembershipTierConfigRepository;
import com.roots.mms.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class MemberService {

    private final MemberRepository memberRepository;
    private final UserRepository userRepository;
    private final UserService userService;
    private final MembershipCategoryConfigRepository categoryRepository;
    private final MembershipTierConfigRepository tierRepository;

    /**
     * Auto-assigns a default membership to a user if they don't already have one.
     * New members start in the PERSONAL category on the FREE tier — the safest
     * entry point. Also populates the legacy {@code membershipType} for
     * backward compatibility with existing admin reports.
     * Called on signup and OAuth provisioning. Silently skips if already a member.
     */
    public void ensureDefaultMembership(User user) {
        if (memberRepository.findByUserId(user.getId()).isPresent()) return;
        String membershipId = generateMembershipId();
        while (memberRepository.existsByMembershipId(membershipId)) membershipId = generateMembershipId();
        UserSummary snapshot = toUserSummary(user);
        Member member = new Member(membershipId, snapshot, MembershipType.BASIC);
        member.setUserId(user.getId());
        member.setMembershipStartDate(java.time.LocalDate.now());
        member.setStatus(MembershipStatus.ACTIVE);
        member.setIsActive(true);
        member.setCategoryCode("PERSONAL");
        member.setTierCode("FREE");
        memberRepository.save(member);
        log.info("Auto-assigned PERSONAL/FREE membership to user {}", user.getUsername());
    }

    public MemberResponse createMember(CreateMemberRequest request) {
        log.info("Creating member for user ID: {}", request.getUserId());

        User user = userRepository.findById(UUID.fromString(request.getUserId()))
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", request.getUserId()));

        if (memberRepository.findByUserId(user.getId()).isPresent()) {
            throw new DuplicateResourceException("Member", "userId", user.getId());
        }

        if (request.getMembershipEndDate() != null && request.getMembershipStartDate() != null
                && request.getMembershipEndDate().isBefore(request.getMembershipStartDate())) {
            throw new BusinessRuleException("The specified date range is not valid.");
        }

        String membershipId = generateMembershipId();
        while (memberRepository.existsByMembershipId(membershipId)) {
            membershipId = generateMembershipId();
        }

        UserSummary snapshot = toUserSummary(user);
        Member member = new Member(membershipId, snapshot, request.getMembershipType());
        member.setUserId(user.getId());
        member.setMembershipStartDate(
                request.getMembershipStartDate() != null ? request.getMembershipStartDate() : LocalDate.now());
        member.setMembershipEndDate(request.getMembershipEndDate());
        member.setNotes(request.getNotes());

        String categoryCode = request.getCategoryCode() != null ? request.getCategoryCode() : "PERSONAL";
        String tierCode = request.getTierCode() != null ? request.getTierCode() : "FREE";
        assertCategoryTierExists(categoryCode, tierCode);
        member.setCategoryCode(categoryCode);
        member.setTierCode(tierCode);

        Member saved = memberRepository.save(member);
        log.info("Created member {} for user {}", saved.getMembershipId(), user.getUsername());
        return toMemberResponse(saved);
    }

    public Optional<MemberResponse> getMemberById(String id) {
        return memberRepository.findById(UUID.fromString(id)).map(this::toMemberResponse);
    }

    public Optional<MemberResponse> getMemberByMembershipId(String membershipId) {
        return memberRepository.findByMembershipId(membershipId).map(this::toMemberResponse);
    }

    public Optional<MemberResponse> getMemberByUserId(String userId) {
        return memberRepository.findByUserId(UUID.fromString(userId)).map(this::toMemberResponse);
    }

    public Page<MemberResponse> getAllMembers(int page, int size, String sortBy, String sortDir) {
        Sort sort = sortDir.equalsIgnoreCase("desc") ? Sort.by(sortBy).descending() : Sort.by(sortBy).ascending();
        Pageable pageable = PageRequest.of(page, size, sort);
        return memberRepository.findAll(pageable).map(this::toMemberResponse);
    }

    public Page<MemberResponse> getMembersByStatus(MembershipStatus status, int page, int size) {
        return memberRepository.findByStatus(status, PageRequest.of(page, size)).map(this::toMemberResponse);
    }

    public Page<MemberResponse> getMembersByType(MembershipType type, int page, int size) {
        return memberRepository.findByMembershipType(type, PageRequest.of(page, size)).map(this::toMemberResponse);
    }

    public Page<MemberResponse> searchMembers(String keyword, int page, int size) {
        return memberRepository.findByKeyword(keyword, PageRequest.of(page, size)).map(this::toMemberResponse);
    }

    public MemberResponse updateMember(String id, UpdateMemberRequest request) {
        log.info("Updating member id={}", id);
        Member member = memberRepository.findById(UUID.fromString(id))
                .orElseThrow(() -> new ResourceNotFoundException("Member", "id", id));

        if (request.getMembershipStartDate() != null && request.getMembershipEndDate() != null
                && request.getMembershipEndDate().isBefore(request.getMembershipStartDate())) {
            throw new BusinessRuleException("The specified date range is not valid.");
        }
        if (request.getMembershipEndDate() != null && member.getMembershipStartDate() != null
                && request.getMembershipEndDate().isBefore(member.getMembershipStartDate())) {
            throw new BusinessRuleException("The specified date range is not valid.");
        }
        if (request.getMembershipStartDate() != null && member.getMembershipEndDate() != null
                && request.getMembershipStartDate().isAfter(member.getMembershipEndDate())) {
            throw new BusinessRuleException("Membership start date cannot be after existing end date");
        }

        if (request.getMembershipType() != null)  member.setMembershipType(request.getMembershipType());
        if (request.getStatus() != null)           member.setStatus(request.getStatus());
        if (request.getMembershipStartDate() != null) member.setMembershipStartDate(request.getMembershipStartDate());
        if (request.getMembershipEndDate() != null)   member.setMembershipEndDate(request.getMembershipEndDate());
        if (request.getNotes() != null)            member.setNotes(request.getNotes());
        if (request.getIsActive() != null)         member.setIsActive(request.getIsActive());

        String newCategory = request.getCategoryCode() != null ? request.getCategoryCode() : member.getCategoryCode();
        String newTier = request.getTierCode() != null ? request.getTierCode() : member.getTierCode();
        if (newCategory != null && newTier != null
                && (request.getCategoryCode() != null || request.getTierCode() != null)) {
            assertCategoryTierExists(newCategory, newTier);
            member.setCategoryCode(newCategory);
            member.setTierCode(newTier);
        }

        // Re-sync user snapshot if user data changed
        userRepository.findById(member.getUserId())
                .ifPresent(u -> member.setUser(toUserSummary(u)));

        return toMemberResponse(memberRepository.save(member));
    }

    public void deleteMember(String id) {
        Member member = memberRepository.findById(UUID.fromString(id))
                .orElseThrow(() -> new ResourceNotFoundException("Member", "id", id));
        memberRepository.delete(member);
        log.info("Deleted member id={}", id);
    }

    public void deactivateMember(String id) {
        Member member = memberRepository.findById(UUID.fromString(id))
                .orElseThrow(() -> new ResourceNotFoundException("Member", "id", id));
        if (!member.getIsActive()) throw new BusinessRuleException("No changes were made.");
        member.setIsActive(false);
        member.setStatus(MembershipStatus.INACTIVE);
        memberRepository.save(member);
    }

    public void activateMember(String id) {
        Member member = memberRepository.findById(UUID.fromString(id))
                .orElseThrow(() -> new ResourceNotFoundException("Member", "id", id));
        if (member.getIsActive()) throw new BusinessRuleException("No changes were made.");
        member.setIsActive(true);
        member.setStatus(MembershipStatus.ACTIVE);
        memberRepository.save(member);
    }

    public long getTotalActiveMembers() {
        return memberRepository.countByStatus(MembershipStatus.ACTIVE);
    }

    /**
     * Self-service tier selection. Assigns {@code categoryCode}/{@code tierCode} to the
     * user's own membership record, creating the record if it doesn't exist yet.
     * Used by the plan-selection flow when a newly-signed-up user lands without
     * a tier.
     */
    public MemberResponse selectTierForUser(String userId, String categoryCode, String tierCode) {
        UUID userUuid = UUID.fromString(userId);
        User user = userRepository.findById(userUuid)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));
        assertCategoryTierExists(categoryCode, tierCode);

        Member member = memberRepository.findByUserId(userUuid).orElse(null);
        if (member == null) {
            String membershipId = generateMembershipId();
            while (memberRepository.existsByMembershipId(membershipId)) membershipId = generateMembershipId();
            UserSummary snapshot = toUserSummary(user);
            member = new Member(membershipId, snapshot, MembershipType.BASIC);
            member.setUserId(user.getId());
            member.setMembershipStartDate(LocalDate.now());
            member.setStatus(MembershipStatus.ACTIVE);
            member.setIsActive(true);
        }
        member.setCategoryCode(categoryCode);
        member.setTierCode(tierCode);
        Member saved = memberRepository.save(member);
        log.info("User {} selected tier {}/{}", user.getUsername(), categoryCode, tierCode);
        return toMemberResponse(saved);
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private String generateMembershipId() {
        return "MEM-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    private void assertCategoryTierExists(String categoryCode, String tierCode) {
        var category = categoryRepository.findByCode(categoryCode)
                .orElseThrow(() -> new BusinessRuleException("Unknown membership category: " + categoryCode));
        if (!Boolean.TRUE.equals(category.getEnabled())) {
            throw new BusinessRuleException("Membership category is disabled: " + categoryCode);
        }
        var tier = tierRepository.findByCategoryCodeAndTierCode(categoryCode, tierCode)
                .orElseThrow(() -> new BusinessRuleException(
                        "Unknown tier for category " + categoryCode + ": " + tierCode));
        if (!Boolean.TRUE.equals(tier.getEnabled())) {
            throw new BusinessRuleException(
                    "Tier is disabled for category " + categoryCode + ": " + tierCode);
        }
    }

    private UserSummary toUserSummary(User user) {
        return new UserSummary(
                user.getId() != null ? user.getId().toString() : null,
                user.getUsername(), user.getEmail(),
                user.getFirstName(), user.getLastName(),
                user.getPhoneNumber(), user.getActive());
    }

    private MemberResponse toMemberResponse(Member member) {
        MemberResponse r = new MemberResponse();
        r.setId(member.getId() != null ? member.getId().toString() : null);
        r.setMembershipId(member.getMembershipId());
        r.setMembershipType(member.getMembershipType());
        r.setCategoryCode(member.getCategoryCode());
        r.setTierCode(member.getTierCode());
        r.setStatus(member.getStatus());
        r.setMembershipStartDate(member.getMembershipStartDate());
        r.setMembershipEndDate(member.getMembershipEndDate());
        r.setNotes(member.getNotes());
        r.setIsActive(member.getIsActive());
        r.setCreatedAt(member.getCreatedAt());
        r.setUpdatedAt(member.getUpdatedAt());

        // Build UserResponse from the embedded snapshot. UserSummary.id is
        // @Transient so we fall back to the authoritative member.userId.
        if (member.getUser() != null) {
            UserSummary s = member.getUser();
            UserResponse ur = new UserResponse();
            String userIdStr = member.getUserId() != null
                    ? member.getUserId().toString()
                    : s.getId();
            ur.setId(userIdStr);
            ur.setUsername(s.getUsername());
            ur.setEmail(s.getEmail());
            ur.setFirstName(s.getFirstName());
            ur.setLastName(s.getLastName());
            ur.setPhoneNumber(s.getPhoneNumber());
            ur.setActive(s.getActive());
            r.setUser(ur);
        }
        return r;
    }
}
