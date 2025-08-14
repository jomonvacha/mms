package com.roots.mms.service;

import com.roots.mms.dto.request.CreateMemberRequest;
import com.roots.mms.dto.request.UpdateMemberRequest;
import com.roots.mms.dto.response.MemberResponse;
import com.roots.mms.dto.response.UserResponse;
import com.roots.mms.entity.Member;
import com.roots.mms.entity.MembershipStatus;
import com.roots.mms.entity.MembershipType;
import com.roots.mms.entity.User;
import com.roots.mms.exception.BusinessRuleException;
import com.roots.mms.exception.DuplicateResourceException;
import com.roots.mms.exception.ResourceNotFoundException;
import com.roots.mms.repository.MemberRepository;
import com.roots.mms.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
@Slf4j
@RequiredArgsConstructor
public class MemberService {

    private final MemberRepository memberRepository;
    private final UserRepository userRepository;

    public MemberResponse createMember(CreateMemberRequest request) {
        log.info("Creating member for user ID: {}", request.getUserId());

        User user = userRepository.findById(request.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", request.getUserId()));

        // Check if user is already a member
        if (memberRepository.findByUserId(user.getId()).isPresent()) {
            throw new DuplicateResourceException("Member", "userId", user.getId());
        }

        // Validate membership dates
        if (request.getMembershipEndDate() != null && request.getMembershipStartDate() != null) {
            if (request.getMembershipEndDate().isBefore(request.getMembershipStartDate())) {
                throw new BusinessRuleException("Membership end date cannot be before start date");
            }
        }

        // Generate unique membership ID
        String membershipId = generateMembershipId();
        while (memberRepository.existsByMembershipId(membershipId)) {
            membershipId = generateMembershipId();
        }

        Member member = new Member();
        member.setMembershipId(membershipId);
        member.setUser(user);
        member.setMembershipType(request.getMembershipType());
        member.setStatus(MembershipStatus.ACTIVE);
        member.setMembershipStartDate(request.getMembershipStartDate() != null ?
                request.getMembershipStartDate() : LocalDate.now());
        member.setMembershipEndDate(request.getMembershipEndDate());
        member.setNotes(request.getNotes());

        Member savedMember = memberRepository.save(member);
        log.info("Successfully created member with ID: {} for user: {}",
                savedMember.getMembershipId(), user.getUsername());

        return convertToMemberResponse(savedMember);
    }

    public Optional<MemberResponse> getMemberById(Long id) {
        return memberRepository.findById(id)
                .map(this::convertToMemberResponse);
    }

    public Optional<MemberResponse> getMemberByMembershipId(String membershipId) {
        return memberRepository.findByMembershipId(membershipId)
                .map(this::convertToMemberResponse);
    }

    public Optional<MemberResponse> getMemberByUserId(Long userId) {
        return memberRepository.findByUserId(userId)
                .map(this::convertToMemberResponse);
    }

    public Page<MemberResponse> getAllMembers(int page, int size, String sortBy, String sortDir) {
        Sort sort = sortDir.equalsIgnoreCase("desc") ?
                Sort.by(sortBy).descending() : Sort.by(sortBy).ascending();
        Pageable pageable = PageRequest.of(page, size, sort);

        return memberRepository.findAll(pageable)
                .map(this::convertToMemberResponse);
    }

    public Page<MemberResponse> getMembersByStatus(MembershipStatus status, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return memberRepository.findByStatus(status, pageable)
                .map(this::convertToMemberResponse);
    }

    public Page<MemberResponse> getMembersByType(MembershipType type, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return memberRepository.findByMembershipType(type, pageable)
                .map(this::convertToMemberResponse);
    }

    public Page<MemberResponse> searchMembers(String keyword, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return memberRepository.findByKeyword(keyword, pageable)
                .map(this::convertToMemberResponse);
    }

    public MemberResponse updateMember(Long id, UpdateMemberRequest request) {
        log.info("Updating member with ID: {}", id);

        Member member = memberRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Member", "id", id));

        // Validate membership dates if both are provided
        if (request.getMembershipStartDate() != null && request.getMembershipEndDate() != null) {
            if (request.getMembershipEndDate().isBefore(request.getMembershipStartDate())) {
                throw new BusinessRuleException("Membership end date cannot be before start date");
            }
        }

        // Validate end date against existing start date
        if (request.getMembershipEndDate() != null && member.getMembershipStartDate() != null) {
            if (request.getMembershipEndDate().isBefore(member.getMembershipStartDate())) {
                throw new BusinessRuleException("Membership end date cannot be before existing start date");
            }
        }

        // Validate start date against existing end date
        if (request.getMembershipStartDate() != null && member.getMembershipEndDate() != null) {
            if (request.getMembershipStartDate().isAfter(member.getMembershipEndDate())) {
                throw new BusinessRuleException("Membership start date cannot be after existing end date");
            }
        }

        if (request.getMembershipType() != null) {
            member.setMembershipType(request.getMembershipType());
        }
        if (request.getStatus() != null) {
            member.setStatus(request.getStatus());
        }
        if (request.getMembershipStartDate() != null) {
            member.setMembershipStartDate(request.getMembershipStartDate());
        }
        if (request.getMembershipEndDate() != null) {
            member.setMembershipEndDate(request.getMembershipEndDate());
        }
        if (request.getNotes() != null) {
            member.setNotes(request.getNotes());
        }
        if (request.getIsActive() != null) {
            member.setIsActive(request.getIsActive());
        }

        Member updatedMember = memberRepository.save(member);
        log.info("Successfully updated member with ID: {}", id);

        return convertToMemberResponse(updatedMember);
    }

    public void deleteMember(Long id) {
        log.info("Deleting member with ID: {}", id);

        Member member = memberRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Member", "id", id));

        memberRepository.delete(member);
        log.info("Successfully deleted member with ID: {}", id);
    }

    public void deactivateMember(Long id) {
        log.info("Deactivating member with ID: {}", id);

        Member member = memberRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Member", "id", id));

        if (!member.getIsActive()) {
            throw new BusinessRuleException("Member is already inactive");
        }

        member.setIsActive(false);
        member.setStatus(MembershipStatus.INACTIVE);
        memberRepository.save(member);

        log.info("Successfully deactivated member with ID: {}", id);
    }

    public void activateMember(Long id) {
        log.info("Activating member with ID: {}", id);

        Member member = memberRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Member", "id", id));

        if (member.getIsActive()) {
            throw new BusinessRuleException("Member is already active");
        }

        member.setIsActive(true);
        member.setStatus(MembershipStatus.ACTIVE);
        memberRepository.save(member);

        log.info("Successfully activated member with ID: {}", id);
    }

    public Long getTotalActiveMembers() {
        return memberRepository.countByStatus(MembershipStatus.ACTIVE);
    }

    private String generateMembershipId() {
        return "MEM-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    private MemberResponse convertToMemberResponse(Member member) {
        MemberResponse response = new MemberResponse();
        response.setId(member.getId());
        response.setMembershipId(member.getMembershipId());
        response.setMembershipType(member.getMembershipType());
        response.setStatus(member.getStatus());
        response.setMembershipStartDate(member.getMembershipStartDate());
        response.setMembershipEndDate(member.getMembershipEndDate());
        response.setNotes(member.getNotes());
        response.setIsActive(member.getIsActive());
        response.setCreatedAt(member.getCreatedAt());
        response.setUpdatedAt(member.getUpdatedAt());

        // Convert user to user response
        UserResponse userResponse = convertToUserResponse(member.getUser());
        response.setUser(userResponse);

        return response;
    }

    private UserResponse convertToUserResponse(User user) {
        UserResponse response = new UserResponse();
        response.setId(user.getId());
        response.setUsername(user.getUsername());
        response.setEmail(user.getEmail());
        response.setFirstName(user.getFirstName());
        response.setLastName(user.getLastName());
        response.setPhoneNumber(user.getPhoneNumber());
        response.setActive(user.getActive());
        response.setCreatedAt(user.getCreatedAt());
        response.setUpdatedAt(user.getUpdatedAt());
        response.setRoles(user.getRoles().stream()
                .map(role -> role.getName().name())
                .collect(Collectors.toList()));
        return response;
    }
}
