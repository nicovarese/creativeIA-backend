package com.ryn.creativeai.api.controller;

import com.ryn.creativeai.core.application.usecase.CreateGenerationJobUseCase;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api") @RequiredArgsConstructor
public class GenerateController {
    private final CreateGenerationJobUseCase useCase;

    @PostMapping("/generate")
    public ResponseEntity<?> generate(@Valid @RequestBody GenerateDTO dto){
        UUID id = useCase.handle(new CreateGenerationJobUseCase.Command(
                dto.template(), dto.version(), dto.provider(), dto.params()
        ));
        return ResponseEntity.accepted().body(Map.of("jobId", id, "status", "QUEUED"));
    }

    public record GenerateDTO(
            @NotBlank String template,
            @NotBlank String version,
            @NotBlank String provider,
            @NotNull Map<String, Object> params
    ) {}
}
