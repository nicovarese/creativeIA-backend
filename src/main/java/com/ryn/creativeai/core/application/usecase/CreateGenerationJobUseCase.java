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
import org.springframework.web.reactive.function.client.WebClient;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
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

    private static final ObjectMapper SCHEMA_MAPPER = new ObjectMapper();

    private static final String DEFAULT_PROVIDER = "comfy"; // key del MockImageProviderAdapter
    private static final String DEFAULT_VERSION  = "v1";   // versión por defecto de plantillas   // <-- ajustá si versionás plantillas

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
    private String selectTemplateKey(Flow flow, int loraCount, int imageCount) {
        String base = switch (flow) {
            case txt2img -> "txt2img";
            case img2img -> "img2img";
            case upscale -> "upscale";
            case mockup  -> "mockup";
        };

        String byLora = switch (loraCount) {
            case 0 -> base;
            case 1 -> base + "_lora1";
            default -> base + "_lora2";
        };

        return (imageCount > 0) ? (byLora + "_img" + imageCount) : byLora;
    }

    public JobResponseDto handle(CreateJobRequestDto req, @Nullable List<MultipartFile> images) {
        // 1) Resolver LoRAs presentes
        var brandSpec = loraCatalog.brandProduct(emptyToNull(req.getBrand()), emptyToNull(req.getProduct()));
        var styleSpec = loraCatalog.style(emptyToNull(req.getStyle()));
        int loraCount = (brandSpec.isPresent()?1:0) + (styleSpec.isPresent()?1:0);
        int countImages = countImages(images, req.getImageUrls());

        // 2) Elegir templateKey según flow y cantidad
        String templateKey = selectTemplateKey(req.getFlow(), loraCount, countImages);

        // 3) Cargar workflow + schema
        TemplateDef def = loadFromClasspath(templateKey);

        // 4) Armar parámetros crudos (los anteriores + los lora_*)
        Map<String,Object> raw = buildRawParams(req, images);

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
        job.setTemplateVer(DEFAULT_VERSION);
        job.setProvider(resolveProvider());                   // o el que corresponda
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
        job.setResolution(req.getResolution());
        job.setScale(req.getScale());
        job.setOffsetX(req.getOffsetX());
        job.setOffsetY(req.getOffsetY());

        job.markQueued();
        jobs.save(job);

        taskExecutor.execute(() -> process(job.getId()));
        return new JobResponseDto(
                job.getId(),
                JobStatus.QUEUED,          // ✅ pasa directamente el enum, no el name()
                req.getFlow().name(),      // ✅ convierte Flow (enum) → String
                List.of(),
                null
        );
    }

    private Map<String, Object> buildRawParams(CreateJobRequestDto r, @Nullable List<MultipartFile> images) {
        Map<String, Object> p = new HashMap<>();

        // comunes
        putIfNotNull(p, "project_id", r.getProjectId());
        putIfNotNull(p, "flow",       r.getFlow().name());
        putIfNotNull(p, "prompt",     r.getPrompt());
        putIfNotNull(p, "width",      r.getWidth());
        putIfNotNull(p, "height",     r.getHeight());
        putIfNotNull(p, "batch",      r.getBatch());

        // estilo / marca / producto
        putIfNotNull(p, "style",      emptyToNull(r.getStyle()));
        putIfNotNull(p, "brand",      emptyToNull(r.getBrand()));
        putIfNotNull(p, "product",    emptyToNull(r.getProduct()));

        // img2img
        putIfNotNull(p, "strength",   r.getStrength());

        // upscale
        putIfNotNull(p, "resolution", r.getResolution());

        // mockup
        putIfNotNull(p, "scale",      r.getScale());
        putIfNotNull(p, "offset_x",   r.getOffsetX());
        putIfNotNull(p, "offset_y",   r.getOffsetY());

        // stage de todas las imágenes
        List<String> comfyFilenames = stageAllToComfyInput(
                images,
                r.getImageUrls(), // List<String>
                Paths.get("C:/Users/natal/AppData/Roaming/StabilityMatrix/Packages/ComfyUI/input")
        );

        // mapeo a image_1, image_2, ...
        for (int i = 0; i < comfyFilenames.size(); i++) {
            p.put("image_" + (i + 1), comfyFilenames.get(i));
        }

        // opcional: compat si algún template viejo usa {{image}}
        if (!comfyFilenames.isEmpty()) {
            p.put("image", comfyFilenames.get(0));
        }

        return p;
    }

    private List<String> stageAllToComfyInput(@Nullable List<MultipartFile> images,
                                              @Nullable List<String> imageUrls,
                                              Path comfyInputDir) {
        List<String> out = new ArrayList<>();

        if (images != null) {
            for (MultipartFile f : images) {
                if (f != null && !f.isEmpty()) {
                    out.add(stageToComfyInput(f, null, comfyInputDir));
                }
            }
        }

        if (imageUrls != null) {
            for (String url : imageUrls) {
                if (url != null && !url.isBlank()) {
                    out.add(stageToComfyInput(null, url, comfyInputDir));
                }
            }
        }

        return out;
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
        try {
            job.markRunning();
            jobs.save(job);
            eventsHub.send(jobId, "STARTED", statusPayload(job));

            // 1) enviar al provider
            job.setProgressSafe(20);
            jobs.save(job);
            eventsHub.send(jobId, "PROGRESS", statusPayload(job));

            var provider = requireProvider(job.getProvider());
            var localPaths = provider.generate(job.getCompiledJson());


            // 2) guardando en storage
            job.setProgressSafe(70);
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
            eventsHub.send(jobId, "FAILED", Map.of(
                    "id", jobId,
                    "status", job.getStatus().name(),
                    "progress", job.getProgress(),
                    "error", job.getErrorMessage()
            ));
        } finally {
            eventsHub.complete(jobId);
        }
    }

    private Map<String,Object> statusPayload(Job j) {
        return Map.of(
                "id", j.getId(),
                "status", j.getStatus().name(),
                "progress", j.getProgress()
        );
    }

    private Map<String,Object> resultPayload(UUID jobId) {
        var imgs = assets.findByJobId(jobId).stream()
                .map(a -> Map.of("url", a.getUrl(), "width", a.getWidth(), "height", a.getHeight()))
                .toList();
        var j = jobs.findById(jobId).orElseThrow();
        return Map.of(
                "id", jobId,
                "status", j.getStatus().name(),
                "progress", j.getProgress(),
                "assets", imgs
        );
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
            var schema = SCHEMA_MAPPER.readTree(schemaJson);
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

    private String resolveProvider() {
        if (providers.containsKey(DEFAULT_PROVIDER)) {
            return DEFAULT_PROVIDER;
        }
        return providers.keySet().stream()
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No hay proveedores de imágenes registrados"));
    }

    private ImageProviderPort requireProvider(String providerKey) {
        return Optional.ofNullable(providers.get(providerKey))
                .orElseThrow(() -> new IllegalStateException("Proveedor no registrado: " + providerKey));
    }


    private String stageToComfyInput(@Nullable MultipartFile image, @Nullable String imageUrl, Path comfyInputDir) {
        try {
            Files.createDirectories(comfyInputDir);

            byte[] bytes;
            String filename;

            if (image != null && !image.isEmpty()) {
                bytes = image.getBytes();
                filename = image.getOriginalFilename();          // ej: 183.JPG
            } else if (imageUrl != null && !imageUrl.isBlank()) {
                bytes = WebClient.create()
                        .get().uri(imageUrl)
                        .retrieve()
                        .bodyToMono(byte[].class)
                        .block(Duration.ofSeconds(30));

                String u = imageUrl;
                int q = u.indexOf('?');
                if (q >= 0) u = u.substring(0, q);
                int slash = u.lastIndexOf('/');
                filename = (slash >= 0) ? u.substring(slash + 1) : u;
            } else {
                return null;
            }

            // Por si viniera con path (aunque decís que no): me quedo solo con el nombre
            filename = Paths.get(filename).getFileName().toString();

            Path dst = comfyInputDir.resolve(filename);

            // SOBRESCRIBIR SIEMPRE
            Files.write(dst, bytes, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

            return filename; // <- mismo filename que venía
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo preparar la imagen de entrada", e);
        }
    }


    private int countImages(@Nullable List<MultipartFile> images, @Nullable List<String> imageUrls) {
        int files = 0;
        if (images != null) {
            for (MultipartFile f : images) {
                if (f != null && !f.isEmpty()) files++;
            }
        }

        int urls = 0;
        if (imageUrls != null) {
            for (String u : imageUrls) {
                if (u != null && !u.isBlank()) urls++;
            }
        }

        return files + urls;
    }


}
