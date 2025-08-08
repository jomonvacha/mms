package com.roots.mms.controller;

import com.roots.mms.dto.request.CreateMemberRequest;
import com.roots.mms.dto.request.UpdateMemberRequest;
import com.roots.mms.dto.response.MemberResponse;
import com.roots.mms.dto.response.MessageResponse;
import com.roots.mms.entity.MembershipStatus;
import com.roots.mms.entity.MembershipType;
import com.roots.mms.service.MemberService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

@CrossOrigin(origins = "*", maxAge = 3600)
@RestController
@RequestMapping("/api/members")
public class MemberController {
    
    @Autowired
    private MemberService memberService;

    @PostMapping
    @PreAuthorize("hasRole('ADMIN') or hasRole('MODERATOR')")
    public ResponseEntity<MemberResponse> createMember(@Valid @RequestBody CreateMemberRequest request) {
        MemberResponse member = memberService.createMember(request);
        return ResponseEntity.ok(member);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('USER') or hasRole('MODERATOR') or hasRole('ADMIN')")
    public ResponseEntity<MemberResponse> getMemberById(@PathVariable Long id) {
        Optional<MemberResponse> member = memberService.getMemberById(id);
        if (member.isPresent()) {
            return ResponseEntity.ok(member.get());
        } else {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/membership/{membershipId}")
    @PreAuthorize("hasRole('USER') or hasRole('MODERATOR') or hasRole('ADMIN')")
    public ResponseEntity<MemberResponse> getMemberByMembershipId(@PathVariable String membershipId) {
        Optional<MemberResponse> member = memberService.getMemberByMembershipId(membershipId);
        if (member.isPresent()) {
            return ResponseEntity.ok(member.get());
        } else {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/user/{userId}")
    @PreAuthorize("hasRole('USER') or hasRole('MODERATOR') or hasRole('ADMIN')")
    public ResponseEntity<MemberResponse> getMemberByUserId(@PathVariable Long userId) {
        Optional<MemberResponse> member = memberService.getMemberByUserId(userId);
        if (member.isPresent()) {
            return ResponseEntity.ok(member.get());
        } else {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping
    @PreAuthorize("hasRole('MODERATOR') or hasRole('ADMIN')")
    public ResponseEntity<Page<MemberResponse>> getAllMembers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir) {
        
        Page<MemberResponse> members = memberService.getAllMembers(page, size, sortBy, sortDir);
        return ResponseEntity.ok(members);
    }

    @GetMapping("/status/{status}")
    @PreAuthorize("hasRole('MODERATOR') or hasRole('ADMIN')")
    public ResponseEntity<Page<MemberResponse>> getMembersByStatus(
            @PathVariable MembershipStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        
        Page<MemberResponse> members = memberService.getMembersByStatus(status, page, size);
        return ResponseEntity.ok(members);
    }

    @GetMapping("/type/{type}")
    @PreAuthorize("hasRole('MODERATOR') or hasRole('ADMIN')")
    public ResponseEntity<Page<MemberResponse>> getMembersByType(
            @PathVariable MembershipType type,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        
        Page<MemberResponse> members = memberService.getMembersByType(type, page, size);
        return ResponseEntity.ok(members);
    }

    @GetMapping("/search")
    @PreAuthorize("hasRole('MODERATOR') or hasRole('ADMIN')")
    public ResponseEntity<Page<MemberResponse>> searchMembers(
            @RequestParam String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        
        Page<MemberResponse> members = memberService.searchMembers(keyword, page, size);
        return ResponseEntity.ok(members);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('MODERATOR')")
    public ResponseEntity<MemberResponse> updateMember(@PathVariable Long id, 
                                         @Valid @RequestBody UpdateMemberRequest request) {
        MemberResponse member = memberService.updateMember(id, request);
        return ResponseEntity.ok(member);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<MessageResponse> deleteMember(@PathVariable Long id) {
        memberService.deleteMember(id);
        return ResponseEntity.ok(new MessageResponse("Member deleted successfully!"));
    }

    @PutMapping("/{id}/deactivate")
    @PreAuthorize("hasRole('ADMIN') or hasRole('MODERATOR')")
    public ResponseEntity<MessageResponse> deactivateMember(@PathVariable Long id) {
        memberService.deactivateMember(id);
        return ResponseEntity.ok(new MessageResponse("Member deactivated successfully!"));
    }

    @PutMapping("/{id}/activate")
    @PreAuthorize("hasRole('ADMIN') or hasRole('MODERATOR')")
    public ResponseEntity<MessageResponse> activateMember(@PathVariable Long id) {
        memberService.activateMember(id);
        return ResponseEntity.ok(new MessageResponse("Member activated successfully!"));
    }

    @GetMapping("/stats/active-count")
    @PreAuthorize("hasRole('MODERATOR') or hasRole('ADMIN')")
    public ResponseEntity<Long> getActiveMembers() {
        Long count = memberService.getTotalActiveMembers();
        return ResponseEntity.ok(count);
    }
}
