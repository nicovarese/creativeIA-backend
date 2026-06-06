package com.ryn.creativeai.core.ports;

import com.ryn.creativeai.core.domain.dto.CreateJobRequestDto;
import com.ryn.creativeai.core.domain.dto.JobResponseDto;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface FlowPort {
    JobResponseDto handle(CreateJobRequestDto req, List<MultipartFile> images);
}
