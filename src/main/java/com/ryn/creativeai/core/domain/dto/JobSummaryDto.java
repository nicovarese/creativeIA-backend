package com.ryn.creativeai.core.domain.dto;


import com.ryn.creativeai.core.domain.model.JobStatus;
import java.time.OffsetDateTime;
import java.util.UUID;

public record JobSummaryDto(
        UUID id,
        UUID projectId,
        JobStatus status,
        String flow,
        Integer progress,
        OffsetDateTime createdAt
) {}
