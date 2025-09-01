package com.ryn.creativeai.infra;

import com.ryn.creativeai.core.domain.model.Asset;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AssetRepository extends JpaRepository<Asset, UUID> {
    List<Asset> findByJobId(UUID jobId);
}

