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
public class Img2ImgAdapter implements FlowPort {

    private final CreateGenerationJobUseCase useCase;

    @Override
    public JobResponseDto handle(CreateJobRequestDto req, MultipartFile image) {
        if (req.getPrompt() == null || req.getPrompt().isBlank())
            throw new IllegalArgumentException("prompt required");
        boolean hasImage = (image != null && !image.isEmpty())
                || (req.getImageUrl() != null && !req.getImageUrl().isBlank());
        if (!hasImage) throw new IllegalArgumentException("image required");
        if (req.getStrength() == null) throw new IllegalArgumentException("strength required");

        return useCase.handle(req, image);
    }
}
