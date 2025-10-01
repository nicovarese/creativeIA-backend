package com.ryn.creativeai.api.controller;

import com.ryn.creativeai.core.application.usecase.GetJobStatusUseCase;
import com.ryn.creativeai.core.domain.dto.JobResponseDto;
import com.ryn.creativeai.core.domain.model.Job;
import com.ryn.creativeai.infra.JobRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/jobs")
@RequiredArgsConstructor
public class JobsController {

    private final GetJobStatusUseCase getJobStatus;
    private final JobRepository jobs;

    @GetMapping("/{id}")
    public ResponseEntity<JobResponseDto> get(@PathVariable UUID id) {
        return getJobStatus.handle(id)
                .map(resp -> ResponseEntity.ok(toDto(resp)))
                .orElse(ResponseEntity.notFound().build());
    }

    private JobResponseDto toDto(GetJobStatusUseCase.Response resp) {
        var images = resp.assets().stream()
                .map(i -> new JobResponseDto.ImageDto(i.url(), i.w(), i.h()))
                .toList();

        return new JobResponseDto(
                resp.jobId(),
                resp.status(),
                resp.flow(),
                images,
                resp.errorMessage()
        );
    }

    /** Lista de jobs por proyecto (paginado) */
    @GetMapping("/project/{projectId}")
    public Page<Job> listByProject(@PathVariable("projectId") UUID projectId,
                                   Pageable pageable) {
        return jobs.findByProjectId(projectId, pageable);
    }


    public record JobResponseDto(
            UUID id,
            String status,
            String flow,
            List<ImageDto> assets,
            String error
    ) {
        public record ImageDto(String url, Integer width, Integer height) {}
    }
}
