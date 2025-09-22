package com.ryn.creativeai.api.controller;


import com.ryn.creativeai.core.application.service.FlowDispatcher;
import com.ryn.creativeai.core.domain.dto.CreateJobRequestDto;
import com.ryn.creativeai.core.domain.dto.JobResponseDto;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/v1/jobs")
@RequiredArgsConstructor
public class GenerateController {

    private final FlowDispatcher dispatcher;

    /** JSON puro (txt2img ó los otros si vienen con imageUrl) */
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public JobResponseDto createJson(@Valid @RequestBody CreateJobRequestDto req) {
        return dispatcher.dispatch(req, null);
    }

    /** multipart: payload (json) + image (file) para img2img/upscale/mockup */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public JobResponseDto createMultipart(@RequestPart("payload") @Valid CreateJobRequestDto req,
                                       @RequestPart(value = "image", required = false) MultipartFile image) {
        return dispatcher.dispatch(req, image);
    }
}
