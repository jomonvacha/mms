package com.roots.mms.repository;

import com.roots.mms.entity.AiModel;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AiModelRepository extends JpaRepository<AiModel, UUID> {
    Optional<AiModel> findByCode(String code);
    boolean existsByCode(String code);
    List<AiModel> findAllByOrderBySortOrderAscDisplayNameAsc();
    List<AiModel> findByStatus(AiModel.Status status);
}
