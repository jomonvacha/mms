package com.roots.mms.repository;

import com.roots.mms.entity.SignupInviteCode;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface SignupInviteCodeRepository extends JpaRepository<SignupInviteCode, UUID> {
    Optional<SignupInviteCode> findByCode(String code);
    boolean existsByCode(String code);
}
