package com.ryn.creativeai.infra;

import com.ryn.creativeai.core.domain.model.BrandLora;
import com.ryn.creativeai.core.domain.model.BrandLoraStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BrandLoraRepository extends JpaRepository<BrandLora, UUID> {

    List<BrandLora> findByOwnerIdOrderByCreatedAtDesc(UUID ownerId);

    List<BrandLora> findByOwnerIdAndStatusOrderByCreatedAtDesc(UUID ownerId, BrandLoraStatus status);

    Optional<BrandLora> findByIdAndOwnerId(UUID id, UUID ownerId);

    Optional<BrandLora> findByOwnerIdAndNameIgnoreCaseAndStatus(UUID ownerId, String name, BrandLoraStatus status);
}
