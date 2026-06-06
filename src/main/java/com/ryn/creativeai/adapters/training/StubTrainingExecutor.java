package com.ryn.creativeai.adapters.training;

import com.ryn.creativeai.core.domain.model.BrandLora;
import com.ryn.creativeai.core.domain.model.BrandLoraStatus;
import com.ryn.creativeai.core.ports.TrainingExecutorPort;
import com.ryn.creativeai.infra.BrandLoraRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.UUID;

/**
 * Simula un training: avanza el progreso paso a paso, deja una safetensors placeholder
 * en {@code creativeai.training.lorasDir} y marca COMPLETED. Si algo explota, marca FAILED.
 *
 * Reemplazar este executor por uno real (ai-toolkit subprocess / Replicate) cuando se
 * defina la infra. Mientras tanto, el resto del ciclo (DTOs, lifecycle, UI) ya queda probado.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "creativeai.training.aitoolkit.enabled", havingValue = "false", matchIfMissing = true)
public class StubTrainingExecutor implements TrainingExecutorPort {

    private final BrandLoraRepository repo;
    private final TaskExecutor taskExecutor;

    @Value("${creativeai.training.lorasDir:./training/loras}")
    private String lorasDir;

    @Value("${creativeai.training.stub.totalSeconds:30}")
    private int totalSeconds;

    @Override
    public void trainAsync(UUID brandLoraId) {
        taskExecutor.execute(() -> run(brandLoraId));
    }

    private void run(UUID brandLoraId) {
        try {
            markRunning(brandLoraId);

            int steps = 10;
            long sleepMs = Math.max(200, (totalSeconds * 1000L) / steps);

            for (int i = 1; i <= steps; i++) {
                Thread.sleep(sleepMs);
                int progress = Math.min(99, (int) Math.round(100.0 * i / steps));
                updateProgress(brandLoraId, progress);
            }

            String path = writePlaceholderSafetensors(brandLoraId);
            markCompleted(brandLoraId, path);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            markFailed(brandLoraId, "interrumpido");
        } catch (Exception e) {
            log.error("Stub training falló para {}", brandLoraId, e);
            markFailed(brandLoraId, e.getMessage());
        }
    }

    @Transactional
    protected void markRunning(UUID id) {
        repo.findById(id).ifPresent(bl -> {
            bl.setStatus(BrandLoraStatus.TRAINING);
            bl.setProgress(1);
            repo.save(bl);
        });
    }

    @Transactional
    protected void updateProgress(UUID id, int p) {
        repo.findById(id).ifPresent(bl -> {
            bl.setProgress(Math.max(0, Math.min(99, p)));
            repo.save(bl);
        });
    }

    @Transactional
    protected void markCompleted(UUID id, String path) {
        repo.findById(id).ifPresent(bl -> {
            bl.setStatus(BrandLoraStatus.COMPLETED);
            bl.setProgress(100);
            bl.setSafetensorsPath(path);
            bl.setCompletedAt(Instant.now());
            repo.save(bl);
        });
    }

    @Transactional
    protected void markFailed(UUID id, String msg) {
        repo.findById(id).ifPresent(bl -> {
            bl.setStatus(BrandLoraStatus.FAILED);
            bl.setErrorMessage(msg);
            repo.save(bl);
        });
    }

    private String writePlaceholderSafetensors(UUID id) throws Exception {
        BrandLora bl = repo.findById(id).orElseThrow();
        String safeName = bl.getName().replaceAll("[^a-zA-Z0-9._-]", "_").toLowerCase();
        Path dir = Paths.get(lorasDir).toAbsolutePath().normalize();
        Files.createDirectories(dir);
        Path out = dir.resolve(safeName + "_" + id + ".safetensors");
        // Placeholder: archivo binario chico para validar el ciclo. Cuando entre un trainer real,
        // este path va a apuntar a la safetensors real generada por ai-toolkit/etc.
        Files.write(out, ("STUB_LORA " + bl.getName() + " " + Instant.now()).getBytes());
        return out.toString();
    }
}
