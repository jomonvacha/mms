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
import java.util.UUID;

@Repository
public interface MemberRepository extends JpaRepository<Member, UUID> {

    Optional<Member> findByMembershipId(String membershipId);

    Optional<Member> findByUserId(UUID userId);

    Page<Member> findByStatus(MembershipStatus status, Pageable pageable);

    Page<Member> findByMembershipType(MembershipType membershipType, Pageable pageable);

    boolean existsByMembershipId(String membershipId);

    /**
     * Case-insensitive search across embedded user snapshot fields and membershipId.
     * Paths match the {@link com.roots.mms.entity.UserSummary} embedded in
     * {@link Member}. The Flyway DDL backs these columns with {@code gin_trgm_ops}
     * indexes so ILIKE '%x%' remains performant on large tables.
     */
    @Query("""
            SELECT m FROM Member m WHERE
              LOWER(m.user.email)     LIKE LOWER(CONCAT('%', :kw, '%')) OR
              LOWER(m.user.firstName) LIKE LOWER(CONCAT('%', :kw, '%')) OR
              LOWER(m.user.lastName)  LIKE LOWER(CONCAT('%', :kw, '%')) OR
              LOWER(m.membershipId)   LIKE LOWER(CONCAT('%', :kw, '%'))
            """)
    Page<Member> findByKeyword(@Param("kw") String kw, Pageable pageable);

    long countByStatus(MembershipStatus status);
}
