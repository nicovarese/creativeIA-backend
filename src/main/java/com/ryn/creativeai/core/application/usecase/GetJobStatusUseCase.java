package com.ryn.creativeai.core.application.usecase;

import com.ryn.creativeai.core.application.service.JobPhaseResolver;
import com.ryn.creativeai.infra.AssetRepository;
import com.ryn.creativeai.infra.JobRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@RequiredArgsConstructor
public class GetJobStatusUseCase {
    private final JobRepository jobs;
    private final AssetRepository assets;

    public Optional<Response> handle(UUID id) {
        return jobs.findById(id).map(this::toResponse);
    }

    public Optional<Response> handle(UUID id, UUID ownerId) {
        return jobs.findByIdAndProjectOwnerId(id, ownerId).map(this::toResponse);
    }

    private Response toResponse(com.ryn.creativeai.core.domain.model.Job j) {
        var a = assets.findByJobId(j.getId());
        return new Response(
                j.getId(),
                j.getStatus().name(),
                j.getFlow(),
                j.getProgress(),
                JobPhaseResolver.resolve(j.getStatus(), j.getProgress()),
                j.getErrorMessage(),
                a.stream().map(aa -> new Image(
                        aa.getUrl(), aa.getWidth(), aa.getHeight(), aa.getMimeType()
                )).toList(),
                j.getSeed()
        );
    }

    public record Response(
            UUID jobId,
            String status,
            String flow,
            Integer progress,
            String phase,
            String errorMessage,
            List<Image> assets,
            Long seed
    ) {}

    public record Image(String url, Integer w, Integer h, String mimeType) {}
}
