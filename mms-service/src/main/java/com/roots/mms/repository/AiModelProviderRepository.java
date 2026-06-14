package com.roots.mms.repository;

import com.roots.mms.entity.AiModelProvider;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface AiModelProviderRepository extends JpaRepository<AiModelProvider, UUID> {
    Optional<AiModelProvider> findByCode(String code);
    boolean existsByCode(String code);
}
