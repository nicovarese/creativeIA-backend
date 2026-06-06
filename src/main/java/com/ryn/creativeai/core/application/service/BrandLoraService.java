package com.ryn.creativeai.core.application.service;

import com.ryn.creativeai.api.dto.BrandLoraDtos.BrandLoraResponse;
import com.ryn.creativeai.api.dto.BrandLoraDtos.CreateBrandLoraReq;
import com.ryn.creativeai.core.domain.model.BrandLora;
import com.ryn.creativeai.core.domain.model.BrandLoraImage;
import com.ryn.creativeai.core.domain.model.BrandLoraStatus;
import com.ryn.creativeai.core.domain.model.User;
import com.ryn.creativeai.core.ports.TrainingExecutorPort;
import com.ryn.creativeai.infra.BrandLoraImageRepository;
import com.ryn.creativeai.infra.BrandLoraRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class BrandLoraService {

    private final BrandLoraRepository repo;
    private final BrandLoraImageRepository imageRepo;
    private final TrainingExecutorPort executor;

    @Value("${creativeai.training.datasetsDir:./training/datasets}")
    private String datasetsDir;

    private static final int MIN_IMAGES = 5;   // tolerante para probar; recomendado 15+
    private static final int MAX_IMAGES = 50;

    @Transactional
    public BrandLoraResponse create(User owner, CreateBrandLoraReq req, List<MultipartFile> images) {
        int n = images == null ? 0 : (int) images.stream().filter(f -> f != null && !f.isEmpty()).count();
        if (n < MIN_IMAGES) {
            throw new IllegalArgumentException("Se requieren al menos " + MIN_IMAGES + " imágenes (recibidas: " + n + ")");
        }
        if (n > MAX_IMAGES) {
            throw new IllegalArgumentException("Máximo " + MAX_IMAGES + " imágenes por dataset (recibidas: " + n + ")");
        }

        BrandLora bl = new BrandLora();
        bl.setOwner(owner);
        bl.setName(req.name().trim());
        bl.setTriggerWord(req.triggerWord().trim().toLowerCase(Locale.ROOT));
        bl.setStatus(BrandLoraStatus.PENDING);
        bl.setProgress(0);
        repo.save(bl);

        Path dir = Paths.get(datasetsDir).toAbsolutePath().normalize().resolve(bl.getId().toString());
        try {
            Files.createDirectories(dir);
        } catch (Exception e) {
            throw new RuntimeException("No se pudo crear el directorio del dataset: " + dir, e);
        }

        int idx = 0;
        for (MultipartFile f : images) {
            if (f == null || f.isEmpty()) continue;
            idx++;
            String original = f.getOriginalFilename() == null ? ("image_" + idx) : f.getOriginalFilename();
            String safe = original.replaceAll("[^a-zA-Z0-9._-]", "_");
            String filename = String.format("%02d_%s", idx, safe);
            Path target = dir.resolve(filename);
            try {
                f.transferTo(target);
            } catch (Exception e) {
                throw new RuntimeException("No se pudo guardar imagen " + filename, e);
            }
            BrandLoraImage bli = new BrandLoraImage();
            bli.setBrandLora(bl);
            bli.setFilename(filename);
            bli.setPath(target.toString());
            imageRepo.save(bli);
        }

        executor.trainAsync(bl.getId());
        return toResponse(bl, n);
    }

    @Transactional(readOnly = true)
    public List<BrandLoraResponse> list(UUID ownerId) {
        return repo.findByOwnerIdOrderByCreatedAtDesc(ownerId).stream()
                .map(bl -> toResponse(bl, (int) imageRepo.countByBrandLoraId(bl.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public BrandLoraResponse get(UUID id, UUID ownerId) {
        var bl = repo.findByIdAndOwnerId(id, ownerId)
                .orElseThrow(() -> new IllegalArgumentException("brand_lora not found"));
        return toResponse(bl, (int) imageRepo.countByBrandLoraId(bl.getId()));
    }

    @Transactional
    public void delete(UUID id, UUID ownerId) {
        var bl = repo.findByIdAndOwnerId(id, ownerId)
                .orElseThrow(() -> new IllegalArgumentException("brand_lora not found"));

        // Borrado físico del dataset y safetensors (best-effort)
        deleteQuietly(Paths.get(datasetsDir).toAbsolutePath().normalize().resolve(bl.getId().toString()));
        if (bl.getSafetensorsPath() != null) {
            deleteQuietly(Paths.get(bl.getSafetensorsPath()));
        }
        imageRepo.deleteByBrandLoraId(bl.getId());
        repo.delete(bl);
    }

    private void deleteQuietly(Path p) {
        try {
            if (Files.isDirectory(p)) {
                Files.walk(p).sorted(java.util.Comparator.reverseOrder())
                        .forEach(child -> { try { Files.deleteIfExists(child); } catch (Exception ignored) {} });
            } else {
                Files.deleteIfExists(p);
            }
        } catch (Exception e) {
            log.warn("No se pudo borrar {}: {}", p, e.toString());
        }
    }

    private BrandLoraResponse toResponse(BrandLora bl, int imageCount) {
        return new BrandLoraResponse(
                bl.getId(),
                bl.getName(),
                bl.getTriggerWord(),
                bl.getStatus(),
                bl.getProgress(),
                bl.getErrorMessage(),
                imageCount,
                bl.getCreatedAt(),
                bl.getCompletedAt()
        );
    }
}
