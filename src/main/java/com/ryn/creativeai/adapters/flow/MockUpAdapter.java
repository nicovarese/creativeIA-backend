package com.ryn.creativeai.adapters.flow;

import com.ryn.creativeai.core.application.usecase.CreateGenerationJobUseCase;
import com.ryn.creativeai.core.domain.dto.CreateJobRequestDto;
import com.ryn.creativeai.core.domain.dto.JobResponseDto;
import com.ryn.creativeai.core.ports.FlowPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Component
@RequiredArgsConstructor
public class MockUpAdapter implements FlowPort {

    private final CreateGenerationJobUseCase useCase;

    @Override
    public JobResponseDto handle(CreateJobRequestDto req, List<MultipartFile> images) {
        boolean hasFiles = (images != null && images.stream().anyMatch(f -> f != null && !f.isEmpty()));
        boolean hasUrls  = req.getImageUrls() != null && req.getImageUrls().stream().anyMatch(u -> u != null && !u.isBlank());
        if (!hasFiles && !hasUrls)
            throw new IllegalArgumentException("image required");
        return useCase.handle(req, images);
    }
}

