package com.ryn.creativeai.core.ports;

import com.ryn.creativeai.core.domain.dto.CreateJobRequestDto;
import com.ryn.creativeai.core.domain.dto.JobResponseDto;
import org.springframework.web.multipart.MultipartFile;

public interface FlowPort {
    JobResponseDto handle(CreateJobRequestDto req, MultipartFile image);
}
