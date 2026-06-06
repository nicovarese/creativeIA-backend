package com.ryn.creativeai.infra;

import com.ryn.creativeai.core.domain.model.Asset;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AssetRepository extends JpaRepository<Asset, UUID> {
    @Query("""
    SELECT a FROM Asset a
    WHERE a.project.id = :projectId
      AND a.project.owner.id = :ownerId
      AND (:qPattern IS NULL OR LOWER(a.url) LIKE :qPattern
           OR LOWER(a.flow) LIKE :qPattern
           OR LOWER(COALESCE(a.displayName, '')) LIKE :qPattern
           OR LOWER(COALESCE(a.prompt, '')) LIKE :qPattern)
      AND (:favoritesOnly = false OR a.favorite = true)
    ORDER BY a.createdAt DESC
  """)
    Page<Asset> search(@Param("projectId") UUID projectId,
                       @Param("ownerId") UUID ownerId,
                       @Param("qPattern") String qPattern,
                       @Param("favoritesOnly") boolean favoritesOnly,
                       Pageable pageable);

    List<Asset> findByJobId(UUID jobId);

    Optional<Asset> findByIdAndProjectIdAndProjectOwnerId(UUID id, UUID projectId, UUID ownerId);
}
