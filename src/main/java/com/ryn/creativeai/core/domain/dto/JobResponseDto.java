package com.ryn.creativeai.core.domain.dto;

import com.ryn.creativeai.core.domain.model.Flow;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data @AllArgsConstructor
public class JobResponseDto {
    private String id;
    private String status;
    private Flow flow;
    private List<Map<String,Object>> results;
    private String error;
}