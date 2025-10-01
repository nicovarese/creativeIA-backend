package com.ryn.creativeai.core.application.usecase;


import com.fasterxml.jackson.databind.ObjectMapper;
import com.ryn.creativeai.core.application.service.JobEventsHub;
import com.ryn.creativeai.core.application.service.LoraCatalog;
import com.ryn.creativeai.core.application.service.ParamResolver;
import com.ryn.creativeai.core.application.service.TemplateCompiler;
import com.ryn.creativeai.core.domain.dto.CreateJobRequestDto;
import com.ryn.creativeai.core.domain.model.Flow;
import com.ryn.creativeai.core.domain.dto.JobResponseDto;
import com.ryn.creativeai.core.domain.model.Asset;
import com.ryn.creativeai.core.domain.model.Job;
import com.ryn.creativeai.core.domain.model.JobProgressStage;
import com.ryn.creativeai.core.domain.model.JobStatus;
import com.ryn.creativeai.core.ports.ImageProviderPort;
import com.ryn.creativeai.core.ports.StoragePort;
import com.ryn.creativeai.core.ports.TemplateRegistryPort;
import com.ryn.creativeai.infra.AssetRepository;
import com.ryn.creativeai.infra.JobRepository;
import com.ryn.creativeai.infra.ProjectRepository;
import jakarta.annotation.Nullable;
import lombok.RequiredArgsConstructor;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Caso de uso: crea un Job de generación de imagen.
 * - Carga workflow+schema del registry
 * - Resuelve/valida parámetros (ParamResolver)
 * - Compila JSON del workflow (TemplateCompiler)
 * - Persiste Job (QUEUED) y lanza procesamiento async (provider -> storage)
 */
@Service
@RequiredArgsConstructor
public class CreateGenerationJobUseCase {

    private static final String DEFAULT_PROVIDER = "comfy"; // <-- ajustá al key de tu ComfyUIAdapter
    private static final String DEFAULT_VERSION  = "v1";    // <-- ajustá si versionás plantillas

    private final ProjectRepository projects;
    private final TemplateRegistryPort registry;
    private final ParamResolver paramResolver;
    private final TemplateCompiler templateCompiler;
    private final Map<String, ImageProviderPort> providers;
    private final StoragePort storage;
    private final JobRepository jobs;
    private final AssetRepository assets;
    private final TaskExecutor taskExecutor;
    private final LoraCatalog loraCatalog;
    private final JobEventsHub eventsHub;

    // decide el templateKey según flow + cantidad de loras
    private String selectTemplateKey(Flow flow, int loraCount) {
        String base = switch (flow) {
            case txt2img -> "txt2img";
            case img2img -> "img2img";
            case upscale -> "upscale";
            case mockup  -> "mockup";
        };
        return switch (loraCount) {
            case 0 -> base;
            case 1 -> base + "_lora1";
            default -> base + "_lora2";
        };
    }

    public JobResponseDto handle(CreateJobRequestDto req, @Nullable MultipartFile image) {
        // 1) Resolver LoRAs presentes
        var brandSpec = loraCatalog.brandProduct(emptyToNull(req.getBrand()), emptyToNull(req.getProduct()));
        var styleSpec = loraCatalog.style(emptyToNull(req.getStyle()));
        int loraCount = (brandSpec.isPresent()?1:0) + (styleSpec.isPresent()?1:0);

        // 2) Elegir templateKey según flow y cantidad
        String templateKey = selectTemplateKey(req.getFlow(), loraCount);

        // 3) Cargar workflow + schema
        TemplateDef def = loadFromClasspath(templateKey);

        // 4) Armar parámetros crudos (los anteriores + los lora_*)
        Map<String,Object> raw = buildRawParams(req, image);

        // Parámetros para templates lora1/lora2
        brandSpec.ifPresent(s -> {
            raw.put("lora_brand_name", s.name());
            raw.put("lora_brand_strength_model", s.strengthModel());
            raw.put("lora_brand_strength_clip",  s.strengthClip());
        });
        styleSpec.ifPresent(s -> {
            raw.put("lora_style_name", s.name());
            raw.put("lora_style_strength_model", s.strengthModel());
            raw.put("lora_style_strength_clip",  s.strengthClip());
        });

        // 🔴 clave: solo mandamos al resolver lo que existe en el schema
        Map<String,Object> templateParams = onlySchemaProps(def.schemaJson(), raw);

        // 5) Validar contra schema + compilar
        Map<String,Object> finalParams = paramResolver.resolve(def.schemaJson(), templateParams);
        String compiled = templateCompiler.compile(def.json(), finalParams);

        // 6) Persistir Job (QUEUED) con Project
        var projectId = UUID.fromString(req.getProjectId()); // asumiendo UUID en el front
        var project = projects.findById(projectId)
                .orElseThrow(() -> new IllegalArgumentException("project not found: " + req.getProjectId()));

        Job job = new Job();
        job.setProject(project);                      // ← RELACIÓN
        job.setTemplateKey(templateKey);
        job.setTemplateVer("v1");                     // o lo que uses
        job.setProvider("mock");                     // o el que corresponda
        job.setCompiledJson(compiled);

        job.setFlow(req.getFlow().name());
        job.setPrompt(req.getPrompt());
        job.setWidth(req.getWidth());
        job.setHeight(req.getHeight());
        job.setBatch(req.getBatch());
        job.setStyle(emptyToNull(req.getStyle()));
        job.setBrand(emptyToNull(req.getBrand()));
        job.setProduct(emptyToNull(req.getProduct()));
        job.setStrength(req.getStrength());
        job.setFactor(req.getFactor());
        job.setTemplate(req.getTemplate());
        job.setScale(req.getScale());
        job.setOffsetX(req.getOffsetX());
        job.setOffsetY(req.getOffsetY());

        job.markQueued();
        jobs.save(job);

        taskExecutor.execute(() -> process(job.getId()));
        return new JobResponseDto(job.getId().toString(), JobStatus.QUEUED.name(), req.getFlow(), List.of(), null);
    }

    /** Convención de nombres para tus templates por flow. Ajustá si usás otra. */
    private String templateKeyForFlow(Flow flow) {
        return switch (flow) {
            case txt2img -> "txt2img";
            case img2img -> "img2img";
            case upscale -> "upscale";
            case mockup  -> "mockup";
        };
    }

    /** Arma el mapa de parámetros que el TemplateCompiler espera (antes del schema). */
    private Map<String, Object> buildRawParams(CreateJobRequestDto r, @Nullable MultipartFile image) {
        Map<String, Object> p = new HashMap<>();

        // comunes
        putIfNotNull(p, "project_id", r.getProjectId());
        putIfNotNull(p, "flow",       r.getFlow().name());
        putIfNotNull(p, "prompt",     r.getPrompt());
        putIfNotNull(p, "width",      r.getWidth());
        putIfNotNull(p, "height",     r.getHeight());
        putIfNotNull(p, "batch",      r.getBatch());

        // estilo / marca / producto (si vienen "Ninguno" el handler ya los convirtió a null)
        putIfNotNull(p, "style",      emptyToNull(r.getStyle()));
        putIfNotNull(p, "brand",      emptyToNull(r.getBrand()));
        putIfNotNull(p, "product",    emptyToNull(r.getProduct()));

        // img2img
        putIfNotNull(p, "strength",   r.getStrength());

        // upscale
        putIfNotNull(p, "factor",     r.getFactor());

        // mockup
        putIfNotNull(p, "template",   r.getTemplate());
        putIfNotNull(p, "scale",      r.getScale());
        putIfNotNull(p, "offset_x",   r.getOffsetX());
        putIfNotNull(p, "offset_y",   r.getOffsetY());

        // input: si hay archivo → guardo TEMP y paso la ruta; si no, paso imageUrl
        String inputPath = null;
        try {
            if (image != null && !image.isEmpty()) {
                Path tmp = Files.createTempFile("creativeai_input_", "_" + image.getOriginalFilename());
                Files.write(tmp, image.getBytes());
                inputPath = tmp.toAbsolutePath().toString();
            } else if (r.getImageUrl() != null && !r.getImageUrl().isBlank()) {
                inputPath = r.getImageUrl(); // remoto o interno
            }
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo preparar la imagen de entrada", e);
        }
        putIfNotNull(p, "input_image", inputPath);

        return p;
    }

    // --- implementación original adaptada ---

    /** holder interno; no dependemos de métodos extras en el puerto */
    private record TemplateDef(String json, String schemaJson) {}

    private TemplateDef loadFromClasspath(String key) {
        // templates/workflows/{key}.json
        // templates/schemas/{key}.schema.json
        String workflowJson = registry.readClasspath("workflows/" + key + ".json");
        String schemaJson   = registry.readClasspath("schemas/" + key + ".schema.json");

        return new TemplateDef(workflowJson, schemaJson);
    }

    private void process(UUID jobId) {
        var job = jobs.findById(jobId).orElseThrow();
        if (job.progressStage() == JobProgressStage.QUEUED) {
            eventsHub.send(jobId, "PROGRESS", statusPayload(job));
        }
        try {
            job.markRunning();
            jobs.save(job);
            eventsHub.send(jobId, "STARTED", statusPayload(job));

            // 1) enviar al provider
            job.moveToStage(JobProgressStage.SENDING_TO_PROVIDER);
            jobs.save(job);
            eventsHub.send(jobId, "PROGRESS", statusPayload(job));

            var localPaths = providers.get(job.getProvider()).generate(job.getCompiledJson());

            // 2) guardando en storage
            job.moveToStage(JobProgressStage.STORING_RESULTS);
            jobs.save(job);
            eventsHub.send(jobId, "PROGRESS", statusPayload(job));

            var stored = storage.store(localPaths);
            for (var si : stored) {
                var a = new Asset();
                a.setJob(job);
                a.setProject(job.getProject());
                a.setFlow(job.getFlow()); // o job.getFlow().name()
                a.setUrl(si.url());
                a.setWidth(si.w() != null ? si.w() : 0);
                a.setHeight(si.h() != null ? si.h() : 0);
                assets.save(a);
            }

            job.markDone();
            jobs.save(job);
            eventsHub.send(jobId, "DONE", resultPayload(jobId));
        } catch (Exception e) {
            job.markFailed(e.getMessage());
            jobs.save(job);
            eventsHub.send(jobId, "FAILED", failurePayload(job));
        } finally {
            eventsHub.complete(jobId);
        }
    }

    private Map<String,Object> statusPayload(Job j) {
        Map<String,Object> payload = new LinkedHashMap<>();
        payload.put("id", j.getId());
        payload.put("status", j.getStatus().name());
        payload.put("progress", j.getProgress());
        payload.put("currentStage", j.progressStage().name());
        payload.put("progressStages", JobProgressStage.definitions());
        return payload;
    }

    private Map<String,Object> resultPayload(UUID jobId) {
        var imgs = assets.findByJobId(jobId).stream()
                .map(a -> Map.of("url", a.getUrl(), "width", a.getWidth(), "height", a.getHeight()))
                .toList();
        var j = jobs.findById(jobId).orElseThrow();
        var payload = statusPayload(j);
        payload.put("assets", imgs);
        return payload;
    }

    private Map<String,Object> failurePayload(Job j) {
        var payload = statusPayload(j);
        payload.put("error", j.getErrorMessage());
        return payload;
    }
    // util
    private static void putIfNotNull(Map<String, Object> map, String k, Object v) {
        if (v != null) map.put(k, v);
    }
    private static String emptyToNull(String s) {
        return (s == null || s.isBlank() || "Ninguno".equalsIgnoreCase(s)) ? null : s;
    }

    // en CreateGenerationJobUseCase (o en un util)
    private Map<String, Object> onlySchemaProps(String schemaJson, Map<String, Object> raw) {
        try {
            var schema = new ObjectMapper().readTree(schemaJson);
            var props = schema.path("properties");
            if (!props.isObject()) return Map.of();
            Map<String, Object> out = new HashMap<>();
            for (var e : raw.entrySet()) {
                if (props.has(e.getKey())) out.put(e.getKey(), e.getValue());
            }
            return out;
        } catch (Exception e) {
            throw new RuntimeException("No pude leer el schema para filtrar props", e);
        }
    }

}
