package com.ryn.creativeai.core.domain.dto;

import com.ryn.creativeai.core.domain.model.JobStatus;
import java.util.List;
import java.util.UUID;

public record JobResponseDto(
        UUID id,
        JobStatus status,
        String flow,
        Integer progress,
        String phase,
        List<JobAssetDto> assets,
        String error,
        Long seed
) {}
