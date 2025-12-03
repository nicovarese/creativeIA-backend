package com.ryn.creativeai.core.domain.dto;

import com.ryn.creativeai.core.domain.model.Flow;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CreateJobRequestDto {
    @NotBlank private String projectId;
    @NotNull private Flow flow;

    private String prompt;
    private Integer width, height, batch;

    private String style;   // "Ninguno" => null
    private String brand;
    private String product;

    // img2img
    private Double strength;
    private String imageUrl;

    // upscale
    private Integer resolution;

    // mockup
    private Integer scale, offsetX, offsetY;
}
