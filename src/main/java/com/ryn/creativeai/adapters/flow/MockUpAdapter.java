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
public class MockUpAdapter implements FlowPort {

    private final CreateGenerationJobUseCase useCase;

    @Override
    public JobResponseDto handle(CreateJobRequestDto req, MultipartFile image) {
        boolean hasImage = (image != null && !image.isEmpty())
                || (req.getImageUrl() != null && !req.getImageUrl().isBlank());
        if (!hasImage) throw new IllegalArgumentException("image required");
        if (req.getTemplate() == null || req.getTemplate().isBlank())
            throw new IllegalArgumentException("template required");

        return useCase.handle(req, image);
    }
}

