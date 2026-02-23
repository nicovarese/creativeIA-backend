package com.ryn.creativeai.core.application.usecase;

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
        return jobs.findById(id).map(j -> {
            var a = assets.findByJobId(id);
            return new Response(
                    j.getId(),
                    j.getStatus().name(),
                    j.getFlow(),
                    j.getErrorMessage(),
                    a.stream().map(aa -> new Image(
                            aa.getUrl(), aa.getWidth(), aa.getHeight()
                    )).toList()
            );
        });
    }

    public Optional<Response> handle(UUID id, UUID ownerId) {
        return jobs.findByIdAndProjectOwnerId(id, ownerId).map(j -> {
            var a = assets.findByJobId(id);
            return new Response(
                    j.getId(),
                    j.getStatus().name(),
                    j.getFlow(),
                    j.getErrorMessage(),
                    a.stream().map(aa -> new Image(
                            aa.getUrl(), aa.getWidth(), aa.getHeight()
                    )).toList()
            );
        });
    }

    //           ↓↓↓  agregado flow y errorMessage
    public record Response(UUID jobId, String status, String flow, String errorMessage, List<Image> assets) {}

    public record Image(String url, Integer w, Integer h) {}
}
