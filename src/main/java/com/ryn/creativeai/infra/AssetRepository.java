package com.ryn.creativeai.infra;

import com.ryn.creativeai.core.domain.model.Asset;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
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
    ORDER BY a.createdAt DESC
  """)
    Page<Asset> search(@Param("projectId") UUID projectId,
                       @Param("ownerId") UUID ownerId,
                       @Param("qPattern") String qPattern,
                       Pageable pageable);

    List<Asset> findByJobId(UUID jobId);

}

