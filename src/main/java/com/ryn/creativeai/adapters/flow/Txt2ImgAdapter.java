package com.ryn.creativeai.adapters.flow;

import com.ryn.creativeai.core.application.usecase.CreateGenerationJobUseCase;
import com.ryn.creativeai.core.domain.dto.CreateJobRequestDto;
import com.ryn.creativeai.core.domain.dto.JobResponseDto;
import com.ryn.creativeai.core.ports.FlowPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

@Component
@RequiredArgsConstructor
public class Txt2ImgAdapter implements FlowPort {

    private final CreateGenerationJobUseCase useCase;

    @Override
    public JobResponseDto handle(CreateJobRequestDto req, MultipartFile image) {
        // Validaciones específicas
        if (req.getPrompt() == null || req.getPrompt().isBlank())
            throw new IllegalArgumentException("prompt required");
        if (req.getWidth() == null || req.getHeight() == null || req.getBatch() == null)
            throw new IllegalArgumentException("width/height/batch required");

        return useCase.handle(req, null); // no necesita archivo
    }
}