package com.ryn.creativeai.core.application.service;

import com.ryn.creativeai.adapters.flow.Img2ImgAdapter;
import com.ryn.creativeai.adapters.flow.MockUpAdapter;
import com.ryn.creativeai.adapters.flow.Txt2ImgAdapter;
import com.ryn.creativeai.adapters.flow.UpscaleAdapter;
import com.ryn.creativeai.core.domain.dto.CreateJobRequestDto;
import com.ryn.creativeai.core.domain.dto.JobResponseDto;
import com.ryn.creativeai.core.domain.model.Flow;
import com.ryn.creativeai.core.ports.FlowPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.EnumMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class FlowDispatcher {

    private final Txt2ImgAdapter txt2img;
    private final Img2ImgAdapter img2img;
    private final UpscaleAdapter upscale;
    private final MockUpAdapter mockup;

    private Map<Flow, FlowPort> map;

    private Map<Flow, FlowPort> handlers() {
        if (map == null) {
            map = new EnumMap<>(Flow.class);
            map.put(Flow.txt2img, txt2img);
            map.put(Flow.img2img, img2img);
            map.put(Flow.upscale,  upscale);
            map.put(Flow.mockup,   mockup);
        }
        return map;
    }

    public JobResponseDto dispatch(CreateJobRequestDto req, MultipartFile image) {
        // normalización de "Ninguno"
        if ("Ninguno".equalsIgnoreCase(req.getStyle()))   req.setStyle(null);
        if ("Ninguno".equalsIgnoreCase(req.getBrand()))   req.setBrand(null);
        if ("Ninguno".equalsIgnoreCase(req.getProduct())) req.setProduct(null);

        FlowPort h = handlers().get(req.getFlow());
        if (h == null) throw new IllegalArgumentException("unsupported flow: " + req.getFlow());
        return h.handle(req, image);
    }
}