package com.ryn.creativeai.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public class ProjectDtos {

    public record CreateProjectRequest(
            @NotBlank(message = "name is required")
            @Size(min = 2, max = 120, message = "name must contain between 2 and 120 characters")
            String name
    ) {}

    public record ProjectResponse(
            UUID id,
            String name,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt
    ) {}

    public record ProjectAssetResponse(
            UUID id,
            String url,
            String flow,
            OffsetDateTime createdAt,
            boolean favorite,
            String mimeType,
            String displayName,
            String prompt,
            Integer width,
            Integer height
    ) {}

    public record ProjectAssetsPage(
            List<ProjectAssetResponse> items,
            int page,
            int size,
            long total
    ) {}
}
