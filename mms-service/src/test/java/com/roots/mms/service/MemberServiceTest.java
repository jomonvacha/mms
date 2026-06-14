package com.roots.mms.service;

import com.roots.mms.AbstractIntegrationTest;
import com.roots.mms.entity.MembershipStatus;
import com.roots.mms.entity.MembershipType;
import com.roots.mms.entity.User;
import com.roots.mms.exception.BusinessRuleException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Extends {@link AbstractIntegrationTest} for the shared Postgres
 * Testcontainer, MockMvc wiring, and automatic per-test cleanup. The
 * previous incarnation spun up its own MongoDBContainer — a leftover from
 * the pre-migration era that would try to reach localhost:5432 on a
 * fresh checkout and fail with "Connection refused".
 */
class MemberServiceTest extends AbstractIntegrationTest {

  @Autowired
  private MemberService memberService;

  private String userId;

  @BeforeEach
  void seedUser() {
    // Base class has already cleared members + users. Seed the one service
    // user these tests operate on.
    User u = new User("svcuser", "svc@example.com", passwordEncoder.encode("p"), "Svc", "User");
    userRepository.save(u);
    userId = u.getId().toString();
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
    // Service message has evolved — assert the exception type and that the
    // message references the date range; don't pin to an exact phrase.
    assertThatThrownBy(() -> memberService.createMember(req))
      .isInstanceOf(BusinessRuleException.class)
      .hasMessageContaining("date");
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
