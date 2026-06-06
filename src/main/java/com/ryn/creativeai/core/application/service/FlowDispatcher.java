package com.ryn.creativeai.core.application.service;

import com.ryn.creativeai.adapters.flow.Image2VideoAdapter;
import com.ryn.creativeai.adapters.flow.Img2ImgAdapter;
import com.ryn.creativeai.adapters.flow.MockUpAdapter;
import com.ryn.creativeai.adapters.flow.ProductSceneAdapter;
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
import java.util.List;
import java.util.Map;

@Service
public class FlowDispatcher {

    private static final String NONE_VALUE = "Ninguno";

    private final Map<Flow, FlowPort> handlers;

    public FlowDispatcher(
            Txt2ImgAdapter txt2img,
            Img2ImgAdapter img2img,
            UpscaleAdapter upscale,
            MockUpAdapter mockup,
            ProductSceneAdapter productScene,
            Image2VideoAdapter image2video
    ) {
        Map<Flow, FlowPort> map = new EnumMap<>(Flow.class);
        map.put(Flow.txt2img, txt2img);
        map.put(Flow.img2img, img2img);
        map.put(Flow.upscale, upscale);
        map.put(Flow.mockup, mockup);
        map.put(Flow.product_scene, productScene);
        map.put(Flow.image2video, image2video);
        this.handlers = Map.copyOf(map);
    }

    public JobResponseDto dispatch(CreateJobRequestDto req, List<MultipartFile> images) {
        req.setStyle(normalizeSelection(req.getStyle()));
        req.setBrand(normalizeSelection(req.getBrand()));
        req.setProduct(normalizeSelection(req.getProduct()));

        FlowPort handler = handlers.get(req.getFlow());
        if (handler == null) {
            throw new IllegalArgumentException("unsupported flow: " + req.getFlow());
        }
        return handler.handle(req, images);
    }

    private static String normalizeSelection(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() || NONE_VALUE.equalsIgnoreCase(trimmed) ? null : trimmed;
    }


}