package com.ryn.creativeai.adapters.training;

import com.ryn.creativeai.core.application.service.GpuMutex;
import com.ryn.creativeai.core.domain.model.BrandLora;
import com.ryn.creativeai.core.domain.model.BrandLoraStatus;
import com.ryn.creativeai.core.ports.TrainingExecutorPort;
import com.ryn.creativeai.infra.BrandLoraImageRepository;
import com.ryn.creativeai.infra.BrandLoraRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.Comparator;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Implementación real con Ostris ai-toolkit.
 *
 * Activación: creativeai.training.aitoolkit.enabled=true (ver application.properties).
 * Cuando está activa, esta clase reemplaza al StubTrainingExecutor.
 *
 * Pipeline:
 *  1. Genera un caption .txt al lado de cada imagen del dataset.
 *  2. Renderiza brand_lora.yaml.template con los valores del BrandLora.
 *  3. Lanza `python run.py <config.yaml>` como subprocess de ai-toolkit.
 *  4. Tail al stdout para parsear progreso (regex de pasos).
 *  5. Al terminar, busca el último safetensors en outputDir y lo copia a lorasDir.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "creativeai.training.aitoolkit.enabled", havingValue = "true")
public class AiToolkitTrainingExecutor implements TrainingExecutorPort {

    private static final Pattern STEP_RE =
            Pattern.compile("\\b(?:step|steps|it)\\D{0,5}(\\d+)\\s*/\\s*(\\d+)\\b", Pattern.CASE_INSENSITIVE);

    private final BrandLoraRepository repo;
    private final BrandLoraImageRepository imageRepo;
    private final GpuMutex gpuMutex;
    private final Executor trainingExecutor;

    @Value("${creativeai.training.lorasDir:./training/loras}")
    private String lorasDir;

    @Value("${creativeai.training.aitoolkit.repoPath}")
    private String aiToolkitRepo;

    @Value("${creativeai.training.aitoolkit.pythonPath}")
    private String pythonPath;

    @Value("${creativeai.training.aitoolkit.baseModel:black-forest-labs/FLUX.2-dev}")
    private String baseModel;

    @Value("${creativeai.training.aitoolkit.steps:3000}")
    private int defaultSteps;

    @Value("${creativeai.training.aitoolkit.outputBase:./training/output}")
    private String outputBase;

    @Value("${creativeai.training.gpuTimeoutHours:6}")
    private int gpuTimeoutHours;

    public AiToolkitTrainingExecutor(BrandLoraRepository repo,
                                     BrandLoraImageRepository imageRepo,
                                     GpuMutex gpuMutex,
                                     @Qualifier("trainingExecutor") Executor trainingExecutor) {
        this.repo = repo;
        this.imageRepo = imageRepo;
        this.gpuMutex = gpuMutex;
        this.trainingExecutor = trainingExecutor;
    }

    @Override
    public void trainAsync(UUID brandLoraId) {
        trainingExecutor.execute(() -> run(brandLoraId));
    }

    private void run(UUID brandLoraId) {
        boolean acquired = false;
        try {
            acquired = gpuMutex.tryAcquire("training-" + brandLoraId, gpuTimeoutHours, TimeUnit.HOURS);
            if (!acquired) {
                markFailed(brandLoraId, "GPU ocupada >" + gpuTimeoutHours + "h");
                return;
            }
            doTraining(brandLoraId);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            markFailed(brandLoraId, "interrumpido");
        } catch (Exception e) {
            log.error("ai-toolkit training falló para {}", brandLoraId, e);
            markFailed(brandLoraId, e.getMessage());
        } finally {
            if (acquired) gpuMutex.release("training-" + brandLoraId);
        }
    }

    private void doTraining(UUID brandLoraId) throws Exception {
        BrandLora bl = repo.findById(brandLoraId).orElseThrow();

        // 1) Generar captions junto a cada imagen del dataset.
        writeCaptions(bl);

        // 2) Renderizar config YAML.
        String name = safeName(bl.getName()) + "_" + brandLoraId.toString().substring(0, 8);
        Path configFile = renderConfig(bl, name);

        // 3) Marcar TRAINING y lanzar subprocess.
        markRunning(brandLoraId);
        log.info("Lanzando ai-toolkit | repo={} python={} config={}", aiToolkitRepo, pythonPath, configFile);

        ProcessBuilder pb = new ProcessBuilder(pythonPath, "run.py", configFile.toString());
        pb.directory(new Path[]{Paths.get(aiToolkitRepo)}[0].toFile());
        pb.redirectErrorStream(true);

        Process proc = pb.start();
        long lastUpdate = 0L;

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(proc.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                log.info("[ai-toolkit {}] {}", brandLoraId.toString().substring(0, 8), line);
                Matcher m = STEP_RE.matcher(line);
                if (m.find()) {
                    int current = Integer.parseInt(m.group(1));
                    int total = Integer.parseInt(m.group(2));
                    int progress = Math.min(99, (int) Math.round(100.0 * current / total));
                    long now = System.currentTimeMillis();
                    // throttle updates a uno cada 3s para no martillar la DB
                    if (now - lastUpdate > 3000) {
                        updateProgress(brandLoraId, progress);
                        lastUpdate = now;
                    }
                }
            }
        }

        int exit = proc.waitFor();
        if (exit != 0) {
            throw new RuntimeException("ai-toolkit terminó con exit code " + exit);
        }

        // 4) Encontrar el safetensors y mover a lorasDir.
        Path finalSafetensors = locateFinalSafetensors(name);
        Path lorasTarget = Paths.get(lorasDir).toAbsolutePath().normalize();
        Files.createDirectories(lorasTarget);
        Path dest = lorasTarget.resolve(name + ".safetensors");
        Files.copy(finalSafetensors, dest, StandardCopyOption.REPLACE_EXISTING);

        markCompleted(brandLoraId, dest.toString());
        log.info("Training {} COMPLETED → {}", brandLoraId, dest);
    }

    /* ---------- helpers ---------- */

    private void writeCaptions(BrandLora bl) throws Exception {
        String productType = (bl.getProductType() == null || bl.getProductType().isBlank())
                ? "product" : bl.getProductType().trim();
        String captionTemplate = "A " + bl.getTriggerWord() + " " + productType
                + ", professional product photography, photorealistic";

        // Por cada imagen del dataset, escribimos un .txt con el mismo nombre base.
        // Si querés captions per-imagen distintas, editá los .txt antes de COMPLETED
        // (en este modelo MVP, todas comparten template).
        var images = imageRepo.findByBrandLoraId(bl.getId());
        for (var img : images) {
            Path imgPath = Paths.get(img.getPath());
            String base = imgPath.getFileName().toString();
            int dot = base.lastIndexOf('.');
            String stem = (dot > 0) ? base.substring(0, dot) : base;
            Path captionPath = imgPath.getParent().resolve(stem + ".txt");
            Files.writeString(captionPath, captionTemplate, StandardCharsets.UTF_8);
        }
        log.info("Captions escritas: {} para BrandLora {}", images.size(), bl.getId());
    }

    private Path renderConfig(BrandLora bl, String name) throws Exception {
        String template;
        try (var in = new ClassPathResource("templates/training/brand_lora.yaml.template").getInputStream()) {
            template = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }

        // Necesitamos la carpeta del dataset (todas las imágenes están en la misma).
        var images = imageRepo.findByBrandLoraId(bl.getId());
        if (images.isEmpty()) throw new IllegalStateException("dataset vacío");
        Path datasetDir = Paths.get(images.get(0).getPath()).getParent().toAbsolutePath().normalize();

        Path outputDir = Paths.get(outputBase).toAbsolutePath().normalize().resolve(name);
        Files.createDirectories(outputDir);

        String productType = (bl.getProductType() == null || bl.getProductType().isBlank())
                ? "product" : bl.getProductType().trim();

        String rendered = template
                .replace("{{name}}", name)
                .replace("{{triggerWord}}", bl.getTriggerWord())
                .replace("{{productType}}", productType)
                .replace("{{datasetDir}}", datasetDir.toString().replace("\\", "/"))
                .replace("{{outputDir}}", outputDir.toString().replace("\\", "/"))
                .replace("{{baseModel}}", baseModel)
                .replace("{{steps}}", String.valueOf(defaultSteps));

        Path configDir = Paths.get(outputBase).toAbsolutePath().normalize().resolve("_configs");
        Files.createDirectories(configDir);
        Path configFile = configDir.resolve(name + ".yaml");
        Files.writeString(configFile, rendered, StandardCharsets.UTF_8);
        return configFile;
    }

    private Path locateFinalSafetensors(String name) throws Exception {
        Path outputDir = Paths.get(outputBase).toAbsolutePath().normalize().resolve(name);
        if (!Files.isDirectory(outputDir)) {
            throw new IllegalStateException("No existe outputDir: " + outputDir);
        }
        try (var stream = Files.walk(outputDir, 3)) {
            return stream
                    .filter(p -> p.toString().toLowerCase().endsWith(".safetensors"))
                    .max(Comparator.comparingLong(p -> p.toFile().lastModified()))
                    .orElseThrow(() -> new IllegalStateException("No se encontró safetensors en " + outputDir));
        }
    }

    private static String safeName(String s) {
        return s.toLowerCase().replaceAll("[^a-z0-9._-]", "_");
    }

    /* ---------- DB updates ---------- */

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
}
