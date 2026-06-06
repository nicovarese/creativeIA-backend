package com.ryn.creativeai.api.dto;

import com.ryn.creativeai.core.domain.model.BrandLoraStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

public class BrandLoraDtos {

    public record CreateBrandLoraReq(
            @NotBlank @Size(min = 2, max = 80) String name,
            @NotBlank @Size(min = 2, max = 40) String triggerWord,
            @Size(max = 80) String productType
    ) {}

    public record BrandLoraResponse(
            UUID id,
            String name,
            String triggerWord,
            String productType,
            BrandLoraStatus status,
            Integer progress,
            String errorMessage,
            int imageCount,
            Instant createdAt,
            Instant completedAt
    ) {}
}
