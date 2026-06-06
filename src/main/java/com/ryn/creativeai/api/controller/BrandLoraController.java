package com.ryn.creativeai.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ryn.creativeai.api.dto.BrandLoraDtos.BrandLoraResponse;
import com.ryn.creativeai.api.dto.BrandLoraDtos.CreateBrandLoraReq;
import com.ryn.creativeai.core.application.service.BrandLoraService;
import com.ryn.creativeai.security.CurrentUserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/brand-loras")
@RequiredArgsConstructor
public class BrandLoraController {

    private final BrandLoraService service;
    private final CurrentUserService currentUser;
    private final ObjectMapper objectMapper;

    /** Crear training: multipart con `payload` (JSON CreateBrandLoraReq) + `images[]`. */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.ACCEPTED)
    public BrandLoraResponse create(@RequestPart("payload") String payloadJson,
                                    @RequestPart("images") List<MultipartFile> images) throws Exception {
        var user = currentUser.requireUser();
        CreateBrandLoraReq req = objectMapper.readValue(payloadJson, CreateBrandLoraReq.class);
        return service.create(user, req, images);
    }

    @GetMapping
    public List<BrandLoraResponse> list() {
        var user = currentUser.requireUser();
        return service.list(user.getId());
    }

    @GetMapping("/{id}")
    public BrandLoraResponse get(@PathVariable UUID id) {
        var user = currentUser.requireUser();
        return service.get(id, user.getId());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        var user = currentUser.requireUser();
        service.delete(id, user.getId());
        return ResponseEntity.noContent().build();
    }
}
