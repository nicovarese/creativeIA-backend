package com.ryn.creativeai.core.application.usecase;


import com.ryn.creativeai.core.application.service.ParamResolver;
import com.ryn.creativeai.core.application.service.TemplateCompiler;
import com.ryn.creativeai.core.domain.dto.CreateJobRequestDto;
import com.ryn.creativeai.core.domain.model.Flow;
import com.ryn.creativeai.core.domain.dto.JobResponseDto;
import com.ryn.creativeai.core.domain.model.Asset;
import com.ryn.creativeai.core.domain.model.Job;
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

    /** Entrada del UC desde los handlers */
    public JobResponseDto handle(CreateJobRequestDto req, @Nullable MultipartFile image) {
        // 1) Armar mapa de parámetros crudos desde el request
        Map<String, Object> rawParams = buildRawParams(req, image);

        // 2) Cargar workflow y schema (por flow)
        String templateKey = templateKeyForFlow(req.getFlow());
        TemplateDef def = loadFromClasspath(templateKey, DEFAULT_VERSION);

        // 3) Resolver/validar parámetros con el schema (defaults + checks)
        Map<String, Object> finalParams = paramResolver.resolve(def.schemaJson(), rawParams);

        // 4) Compilar el workflow con placeholders → JSON final para el provider
        String compiled = templateCompiler.compile(def.json(), finalParams);

        // 5) Persistir Job (QUEUED) con Project
        var projectId = UUID.fromString(req.getProjectId()); // asumiendo UUID en el front
        var project = projects.findById(projectId)
                .orElseThrow(() -> new IllegalArgumentException("project not found: " + req.getProjectId()));

        Job job = new Job();
        job.setProject(project);                      // ← RELACIÓN
        job.setTemplateKey(templateKey);
        job.setTemplateVer("v1");                     // o lo que uses
        job.setProvider("comfy");                     // o el que corresponda
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

    private TemplateDef loadFromClasspath(String key, String ver) {
        // templates/workflows/{key}_{ver}.json
        // templates/schemas/{key}_{ver}.schema.json
        String base = key + "_" + ver;
        String workflowJson = registry.readClasspath("workflows/" + base + ".json");
        String schemaJson   = registry.readClasspath("schemas/" + base + ".schema.json");
        return new TemplateDef(workflowJson, schemaJson);
    }

    private void process(UUID jobId) {
        Job job = jobs.findById(jobId).orElseThrow();
        job.markRunning();
        jobs.save(job);

        ImageProviderPort provider = providers.get(job.getProvider());
        if (provider == null) {
            job.markFailed("Provider desconocido: " + job.getProvider());
            jobs.save(job);
            return;
        }

        try {
            // 1) Ejecutar provider → devuelve paths locales o URLs (según tu adapter)
            var outputs = provider.generate(job.getCompiledJson()); // mantiene tu firma actual

            // 2) Subir resultados a storage (si provider devolvió paths locales)
            var stored = storage.store(outputs); // mantiene tu firma actual (lista → urls + dims)

            // 3) Persistir assets
            for (var si : stored) {
                Asset a = new Asset();
                a.setJob(job);
                a.setUrl(si.url());
                a.setWidth(si.w());
                a.setHeight(si.h());
                assets.save(a);
            }
            job.markDone();
        } catch (Exception e) {
            job.markFailed(e.getMessage());
        } finally {
            jobs.save(job);
        }
    }

    // util
    private static void putIfNotNull(Map<String, Object> map, String k, Object v) {
        if (v != null) map.put(k, v);
    }
    private static String emptyToNull(String s) {
        return (s == null || s.isBlank() || "Ninguno".equalsIgnoreCase(s)) ? null : s;
    }
}
