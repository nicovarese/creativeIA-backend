package com.ryn.creativeai.api.controller;

import com.ryn.creativeai.core.application.usecase.GetJobStatusUseCase;
import com.ryn.creativeai.core.domain.model.Job;
import com.ryn.creativeai.core.domain.model.JobStatus;
import com.ryn.creativeai.core.domain.dto.JobAssetDto;
import com.ryn.creativeai.core.domain.dto.JobResponseDto;
import com.ryn.creativeai.core.domain.dto.JobSummaryDto;
import com.ryn.creativeai.infra.JobRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.UUID;

import static org.springframework.http.HttpStatus.*;

@RestController
@RequestMapping("/v1/jobs")
@RequiredArgsConstructor
public class JobsController {

    private final GetJobStatusUseCase getJobStatus;
    private final JobRepository jobs;

    /** Detalle por id (para polling) */
    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    public ResponseEntity<JobResponseDto> get(@PathVariable UUID id) {
        return getJobStatus.handle(id)
                .map(resp -> ResponseEntity.ok(toDto(resp)))
                .orElse(ResponseEntity.notFound().build());
    }

    /** Redirect al resultado si está listo (opcional) */
    @GetMapping("/{id}/result")
    @Transactional(readOnly = true)
    public ResponseEntity<Void> result(@PathVariable UUID id) {
        // Usamos el use case que ya agrega los assets
        var resp = getJobStatus.handle(id)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Job not found"));

        var status = parseStatus(resp.status());
        if (status != JobStatus.DONE || resp.assets() == null || resp.assets().isEmpty()) {
            throw new ResponseStatusException(CONFLICT, "Job not ready");
        }

        // Elegimos el asset “mejor” (mayor área); si querés el primero, quedate con resp.assets().get(0)
        var best = resp.assets().stream()
                .filter(a -> a != null && a.url() != null)
                .max((a, b) -> {
                    long aw = a.w() == null ? 0 : a.w();
                    long ah = a.h() == null ? 0 : a.h();
                    long bw = b.w() == null ? 0 : b.w();
                    long bh = b.h() == null ? 0 : b.h();
                    return Long.compare(aw * ah, bw * bh);
                })
                .orElseThrow(() -> new ResponseStatusException(CONFLICT, "Job has no downloadable assets"));

        return ResponseEntity.status(FOUND)
                .header(HttpHeaders.LOCATION, best.url())
                .build();
    }


    /** Lista de jobs por proyecto (paginado) */
    @GetMapping("/project/{projectId}")
    @Transactional(readOnly = true)
    public Page<JobSummaryDto> listByProject(@PathVariable("projectId") UUID projectId,
                                             Pageable pageable) {
        // El repo devuelve Page<Job> (dominio), así que mapeamos desde Job, no JobEntity
        return jobs.findByProjectId(projectId, pageable)
                .map(this::toSummaryDto);
    }

    /* =================== mappers =================== */

    private JobResponseDto toDto(GetJobStatusUseCase.Response resp) {
        var images = resp.assets().stream()
                .map(i -> new JobAssetDto(i.url(), i.w(), i.h()))
                .toList();

        return new JobResponseDto(
                resp.jobId(),
                parseStatus(resp.status()),   // << String -> JobStatus
                resp.flow(),
                images,
                resp.errorMessage()
        );
    }

    private JobSummaryDto toSummaryDto(Job j) {
        // createdAt: Instant -> OffsetDateTime (UTC)
        OffsetDateTime createdAt = j.getCreatedAt() == null
                ? null
                : OffsetDateTime.ofInstant(j.getCreatedAt(), ZoneOffset.UTC);

        return new JobSummaryDto(
                j.getId(),
                j.getProject().getId(),
                j.getStatus(),     // ya es JobStatus (enum)
                j.getFlow(),
                j.getProgress(),
                createdAt
        );
    }

    /**
     * Acepta strings como "done", "DONE", "Succeeded" (mapea a DONE), etc.
     * Si no reconoce, cae en FAILED así no rompe el contrato del DTO.
     */
    private static JobStatus parseStatus(String raw) {
        if (raw == null) return JobStatus.FAILED;
        String s = raw.trim().toUpperCase(Locale.ROOT);
        // Normalizamos variantes comunes
        if (s.equals("SUCCEEDED") || s.equals("SUCCESS")) s = "DONE";
        try {
            return JobStatus.valueOf(s);
        } catch (IllegalArgumentException ex) {
            return JobStatus.FAILED;
        }
    }
}
