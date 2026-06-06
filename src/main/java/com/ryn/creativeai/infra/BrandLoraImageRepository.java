package com.ryn.creativeai.infra;

import com.ryn.creativeai.core.domain.model.BrandLoraImage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface BrandLoraImageRepository extends JpaRepository<BrandLoraImage, UUID> {

    List<BrandLoraImage> findByBrandLoraId(UUID brandLoraId);

    long countByBrandLoraId(UUID brandLoraId);

    void deleteByBrandLoraId(UUID brandLoraId);
}
