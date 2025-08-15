package com.roots.mms.repository;

import com.roots.mms.entity.Member;
import com.roots.mms.entity.MembershipStatus;
import com.roots.mms.entity.MembershipType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface MemberRepository extends JpaRepository<Member, Long> {

  Optional<Member> findByMembershipId(String membershipId);

  Optional<Member> findByUserId(Long userId);

  Page<Member> findByStatus(MembershipStatus status, Pageable pageable);

  Page<Member> findByMembershipType(MembershipType membershipType, Pageable pageable);

  Boolean existsByMembershipId(String membershipId);

  @Query("SELECT m FROM Member m WHERE " +
    "m.user.firstName LIKE %:keyword% OR " +
    "m.user.lastName LIKE %:keyword% OR " +
    "m.user.email LIKE %:keyword% OR " +
    "m.membershipId LIKE %:keyword%")
  Page<Member> findByKeyword(@Param("keyword") String keyword, Pageable pageable);

  @Query("SELECT COUNT(m) FROM Member m WHERE m.status = :status")
  Long countByStatus(@Param("status") MembershipStatus status);
}
