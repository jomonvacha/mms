package com.roots.mms.service;

import com.roots.mms.entity.MembershipStatus;
import com.roots.mms.entity.MembershipType;
import com.roots.mms.entity.User;
import com.roots.mms.exception.BusinessRuleException;
import com.roots.mms.repository.MemberRepository;
import com.roots.mms.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.security.oauth2.client.OAuth2ClientAutoConfiguration,org.springframework.boot.autoconfigure.security.oauth2.client.servlet.OAuth2ClientWebSecurityAutoConfiguration"
})
@ActiveProfiles("test")
class MemberServiceTest {

    @Autowired
    private MemberService memberService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private MemberRepository memberRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    private Long userId;

    @BeforeEach
    void setup() {
        memberRepository.deleteAll();
        userRepository.deleteAll();
        User u = new User("svcuser", "svc@example.com", passwordEncoder.encode("p"), "Svc", "User");
        userRepository.save(u);
        userId = u.getId();
    }

    @Test
    void createMember_valid_and_countActive() {
        var req = new com.roots.mms.dto.request.CreateMemberRequest();
        req.setUserId(userId);
        req.setMembershipType(MembershipType.BASIC);
        var res = memberService.createMember(req);
        assertThat(res.getId()).isNotNull();
        assertThat(memberService.getTotalActiveMembers()).isEqualTo(1L);
    }

    @Test
    void createMember_invalidDates_throws() {
        var req = new com.roots.mms.dto.request.CreateMemberRequest();
        req.setUserId(userId);
        req.setMembershipType(MembershipType.BASIC);
        req.setMembershipStartDate(LocalDate.now());
        req.setMembershipEndDate(LocalDate.now().minusDays(1));
        assertThatThrownBy(() -> memberService.createMember(req))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("end date cannot be before start date");
    }

    @Test
    void activate_and_deactivate() {
        var req = new com.roots.mms.dto.request.CreateMemberRequest();
        req.setUserId(userId);
        req.setMembershipType(MembershipType.BASIC);
        var res = memberService.createMember(req);
        var id = res.getId();

        memberService.deactivateMember(id);
        assertThat(memberService.getMembersByStatus(MembershipStatus.INACTIVE, 0, 10).getTotalElements()).isEqualTo(1);

        memberService.activateMember(id);
        assertThat(memberService.getMembersByStatus(MembershipStatus.ACTIVE, 0, 10).getTotalElements()).isEqualTo(1);
    }
}
