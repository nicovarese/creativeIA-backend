package com.ryn.creativeai.core.application.usecase;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ryn.creativeai.api.exception.TemplateLoadingException;
import com.ryn.creativeai.core.application.service.JobPhaseResolver;
import com.ryn.creativeai.core.application.service.LoraCatalog;
import com.ryn.creativeai.core.application.service.ParamResolver;
import com.ryn.creativeai.core.application.service.TemplateCompiler;
import com.ryn.creativeai.core.domain.dto.CreateJobRequestDto;
import com.ryn.creativeai.core.domain.dto.JobResponseDto;
import com.ryn.creativeai.core.domain.model.Asset;
import com.ryn.creativeai.core.domain.model.Flow;
import com.ryn.creativeai.core.domain.model.Job;
import com.ryn.creativeai.core.domain.model.JobStatus;
import com.ryn.creativeai.core.ports.ImageProviderPort;
import com.ryn.creativeai.core.ports.StoragePort;
import com.ryn.creativeai.core.ports.TemplateRegistryPort;
import com.ryn.creativeai.infra.AssetRepository;
import com.ryn.creativeai.infra.JobRepository;
import com.ryn.creativeai.infra.ProjectRepository;
import com.ryn.creativeai.security.CurrentUserService;
import jakarta.annotation.Nullable;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
@RequiredArgsConstructor
@Slf4j
public class CreateGenerationJobUseCase {

    private static final ObjectMapper SCHEMA_MAPPER = new ObjectMapper();
    private static final String DEFAULT_PROVIDER = "comfyui";
    private static final String DEFAULT_VERSION = "v1";

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
    private final CurrentUserService currentUser;

    @Value("${comfy.inputDir:./ComfyUI/input}")
    private String comfyInputDir;

    @Value("${storage.assetsDir:./assets}")
    private String assetsDir;

    @Value("${app.publicBaseUrl:http://localhost:8080}")
    private String publicBaseUrl;

    @Value("${creativeai.generation.fallback-to-mock-on-error:true}")
    private boolean fallbackToMockOnError;

    public JobResponseDto handle(CreateJobRequestDto req, @Nullable List<MultipartFile> images) {
        if (req.getSeed() == null) {
            req.setSeed((long) ThreadLocalRandom.current().nextInt(1, Integer.MAX_VALUE));
        }

        var brandSpec = loraCatalog.brandProduct(emptyToNull(req.getBrand()), emptyToNull(req.getProduct()));
        var styleSpec = loraCatalog.style(emptyToNull(req.getStyle()));
        int loraCount = (brandSpec.isPresent() ? 1 : 0) + (styleSpec.isPresent() ? 1 : 0);
        int imageCount = countImages(images, req.getImageUrls());

        ResolvedTemplate resolved = resolveTemplate(req.getFlow(), loraCount, imageCount);
        String templateKey = resolved.key();
        TemplateDef def = resolved.def();

        Map<String, Object> raw = buildRawParams(req, images);
        brandSpec.ifPresent(spec -> {
            raw.put("lora_brand_name", spec.name());
            raw.put("lora_brand_strength_model", spec.strengthModel());
            raw.put("lora_brand_strength_clip", spec.strengthClip());
        });
        styleSpec.ifPresent(spec -> {
            raw.put("lora_style_name", spec.name());
            raw.put("lora_style_strength_model", spec.strengthModel());
            raw.put("lora_style_strength_clip", spec.strengthClip());
        });

        Map<String, Object> templateParams = onlySchemaProps(def.schemaJson(), raw);
        Map<String, Object> finalParams = paramResolver.resolve(def.schemaJson(), templateParams);
        String compiled = templateCompiler.compile(def.json(), finalParams);

        UUID projectId = parseProjectId(req.getProjectId());
        var owner = currentUser.requireUser();
        var project = projects.findByIdAndOwnerId(projectId, owner.getId())
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "project not found"));

        Job job = new Job();
        job.setProject(project);
        job.setTemplateKey(templateKey);
        job.setTemplateVer(DEFAULT_VERSION);
        job.setProvider(resolveProvider());
        job.setCompiledJson(compiled);
        job.setFlow(req.getFlow().name());
        job.setPrompt(req.getPrompt());
        job.setWidth(req.getWidth());
        job.setHeight(req.getHeight());
        job.setBatch(req.getBatch());
        job.setStyle(emptyToNull(req.getStyle()));
        job.setBrand(emptyToNull(req.getBrand()));
        job.setProduct(emptyToNull(req.getProduct()));
        job.setSeed(req.getSeed());
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
                JobStatus.QUEUED,
                req.getFlow().name(),
                job.getProgress(),
                JobPhaseResolver.resolve(job.getStatus(), job.getProgress()),
                List.of(),
                null,
                job.getSeed()
        );
    }

    private UUID parseProjectId(String projectId) {
        if (projectId == null || projectId.isBlank()) {
            throw new IllegalArgumentException("projectId must be a valid UUID");
        }
        try {
            return UUID.fromString(projectId);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("projectId must be a valid UUID", ex);
        }
    }

    private List<String> templateCandidates(Flow flow, int loraCount, int imageCount) {
        String base = switch (flow) {
            case txt2img -> "txt2img";
            case img2img -> "img2img";
            case upscale -> "upscale";
            case mockup -> "mockup";
            case product_scene -> "product_scene";
            case image2video -> "image2video";
        };

        List<String> loraSuffixes = new ArrayList<>();
        for (int l = Math.min(loraCount, 2); l >= 0; l--) {
            loraSuffixes.add(l == 0 ? "" : "_lora" + l);
        }

        List<String> candidates = new ArrayList<>();
        for (int n = imageCount; n >= 1; n--) {
            for (String suffix : loraSuffixes) {
                candidates.add(base + suffix + "_img" + n);
            }
        }
        for (String suffix : loraSuffixes) {
            candidates.add(base + suffix);
        }
        return candidates;
    }

    private Map<String, Object> buildRawParams(CreateJobRequestDto request, @Nullable List<MultipartFile> images) {
        Map<String, Object> params = new HashMap<>();
        putIfNotNull(params, "project_id", request.getProjectId());
        putIfNotNull(params, "flow", request.getFlow().name());
        putIfNotNull(params, "prompt", request.getPrompt());
        putIfNotNull(params, "width", request.getWidth());
        putIfNotNull(params, "height", request.getHeight());
        putIfNotNull(params, "batch", request.getBatch());
        putIfNotNull(params, "seed", request.getSeed());
        putIfNotNull(params, "style", emptyToNull(request.getStyle()));
        putIfNotNull(params, "brand", emptyToNull(request.getBrand()));
        putIfNotNull(params, "product", emptyToNull(request.getProduct()));
        putIfNotNull(params, "strength", request.getStrength());
        putIfNotNull(params, "resolution", request.getResolution());
        putIfNotNull(params, "scale", request.getScale());
        putIfNotNull(params, "offset_x", request.getOffsetX());
        putIfNotNull(params, "offset_y", request.getOffsetY());

        List<String> stagedImages = stageAllToComfyInput(images, request.getImageUrls(), Paths.get(comfyInputDir));
        for (int i = 0; i < stagedImages.size(); i++) {
            params.put("image_" + (i + 1), stagedImages.get(i));
        }
        if (!stagedImages.isEmpty()) {
            params.put("image", stagedImages.get(0));
        }

        return params;
    }

    private List<String> stageAllToComfyInput(@Nullable List<MultipartFile> images,
                                              @Nullable List<String> imageUrls,
                                              Path comfyInputDir) {
        List<String> staged = new ArrayList<>();

        if (images != null) {
            for (MultipartFile image : images) {
                if (image != null && !image.isEmpty()) {
                    staged.add(stageToComfyInput(image, null, comfyInputDir));
                }
            }
        }

        if (imageUrls != null) {
            for (String url : imageUrls) {
                if (url != null && !url.isBlank()) {
                    staged.add(stageToComfyInput(null, url, comfyInputDir));
                }
            }
        }

        return staged;
    }

    private record TemplateDef(String json, String schemaJson) {}

    private record ResolvedTemplate(String key, TemplateDef def) {}

    private ResolvedTemplate resolveTemplate(Flow flow, int loraCount, int imageCount) {
        List<String> candidates = templateCandidates(flow, loraCount, imageCount);
        TemplateLoadingException last = null;
        for (String key : candidates) {
            try {
                TemplateDef def = loadFromClasspath(key);
                if (!candidates.get(0).equals(key)) {
                    log.warn("Template '{}' no encontrado, usando fallback '{}'", candidates.get(0), key);
                }
                return new ResolvedTemplate(key, def);
            } catch (TemplateLoadingException ex) {
                last = ex;
            }
        }
        throw new TemplateLoadingException(
                "Ningun template disponible para flow=" + flow + " (probados: " + candidates + ")",
                last
        );
    }

    private TemplateDef loadFromClasspath(String key) {
        String workflowJson = registry.readClasspath("workflows/" + key + ".json");
        String schemaJson = registry.readClasspath("schemas/" + key + ".schema.json");
        return new TemplateDef(workflowJson, schemaJson);
    }

    private void process(UUID jobId) {
        Job job = jobs.findById(jobId).orElseThrow();
        try {
            job.markRunning();
            job.setProgressSafe(10);
            jobs.save(job);

            job.setProgressSafe(35);
            jobs.save(job);

            ImageProviderPort provider = requireProvider(job.getProvider());
            List<String> localPaths = generateWithFallback(provider, job);

            job.setProgressSafe(70);
            jobs.save(job);

            List<StoragePort.StoredImage> storedImages = storage.store(localPaths);
            for (StoragePort.StoredImage stored : storedImages) {
                Asset asset = new Asset();
                asset.setJob(job);
                asset.setProject(job.getProject());
                asset.setFlow(job.getFlow());
                asset.setUrl(stored.url());
                asset.setDisplayName(stored.fileName());
                asset.setPrompt(job.getPrompt());
                asset.setWidth(stored.w());
                asset.setHeight(stored.h());
                if (stored.mimeType() != null) {
                    asset.setMimeType(stored.mimeType());
                }
                assets.save(asset);
            }

            job.markDone();
            jobs.save(job);
        } catch (Exception ex) {
            job.markFailed(ex.getMessage());
            jobs.save(job);
        }
    }

    private static void putIfNotNull(Map<String, Object> map, String key, Object value) {
        if (value != null) {
            map.put(key, value);
        }
    }

    private static String emptyToNull(String value) {
        return (value == null || value.isBlank() || "Ninguno".equalsIgnoreCase(value)) ? null : value;
    }

    private Map<String, Object> onlySchemaProps(String schemaJson, Map<String, Object> raw) {
        try {
            var schema = SCHEMA_MAPPER.readTree(schemaJson);
            var props = schema.path("properties");
            if (!props.isObject()) {
                return Map.of();
            }
            Map<String, Object> filtered = new HashMap<>();
            for (var entry : raw.entrySet()) {
                if (props.has(entry.getKey())) {
                    filtered.put(entry.getKey(), entry.getValue());
                }
            }
            return filtered;
        } catch (Exception ex) {
            throw new RuntimeException("No pude leer el schema para filtrar props", ex);
        }
    }

    private String resolveProvider() {
        if (providers.containsKey(DEFAULT_PROVIDER)) {
            return DEFAULT_PROVIDER;
        }
        return providers.keySet().stream()
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No hay proveedores de imagenes registrados"));
    }

    private ImageProviderPort requireProvider(String providerKey) {
        return Optional.ofNullable(providers.get(providerKey))
                .orElseThrow(() -> new IllegalStateException("Proveedor no registrado: " + providerKey));
    }

    private List<String> generateWithFallback(ImageProviderPort provider, Job job) throws Exception {
        try {
            return provider.generate(job.getCompiledJson());
        } catch (Exception ex) {
            if (!fallbackToMockOnError || !"comfyui".equalsIgnoreCase(job.getProvider())) {
                throw ex;
            }
            ImageProviderPort mockProvider = providers.get("mock");
            if (mockProvider == null) {
                throw ex;
            }
            log.warn("ComfyUI fallo para job {}. Se aplica fallback mock temporal: {}", job.getId(), ex.getMessage());
            return mockProvider.generate(job.getCompiledJson());
        }
    }

    private String stageToComfyInput(@Nullable MultipartFile image, @Nullable String imageUrl, Path comfyInputDir) {
        try {
            Files.createDirectories(comfyInputDir);

            byte[] bytes;
            String sourceName;

            if (image != null && !image.isEmpty()) {
                bytes = image.getBytes();
                sourceName = image.getOriginalFilename();
            } else if (imageUrl != null && !imageUrl.isBlank()) {
                Path sourcePath = resolveAllowedAssetPath(imageUrl);
                bytes = Files.readAllBytes(sourcePath);
                sourceName = sourcePath.getFileName().toString();
            } else {
                return null;
            }

            String cleanName = Paths.get(sourceName == null ? "asset.png" : sourceName).getFileName().toString();
            String extension = resolveExtension(cleanName);
            String fileName = UUID.randomUUID().toString().replace("-", "") + extension;
            Path destination = comfyInputDir.resolve(fileName);
            Files.write(destination, bytes, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            return fileName;
        } catch (Exception ex) {
            throw new IllegalStateException("No se pudo preparar la imagen de entrada", ex);
        }
    }

    private Path resolveAllowedAssetPath(String imageUrl) {
        String relativeAssetPath = toRelativeAssetPath(imageUrl);
        if (relativeAssetPath == null) {
            throw new IllegalArgumentException("Solo se permiten imageUrls de assets locales del proyecto");
        }

        Path root = Path.of(assetsDir).toAbsolutePath().normalize();
        String fileName = Paths.get(relativeAssetPath.substring("/assets/".length())).getFileName().toString();
        Path resolved = root.resolve(fileName).normalize();
        if (!resolved.startsWith(root) || !Files.exists(resolved)) {
            throw new IllegalArgumentException("Asset no encontrado para imageUrl");
        }
        return resolved;
    }

    private String toRelativeAssetPath(String imageUrl) {
        if (imageUrl.startsWith("/assets/")) {
            return imageUrl;
        }
        try {
            URI assetUri = new URI(imageUrl);
            URI publicUri = new URI(publicBaseUrl);
            boolean sameHost = Objects.equals(assetUri.getHost(), publicUri.getHost())
                    && effectivePort(assetUri) == effectivePort(publicUri);
            if (sameHost && assetUri.getPath() != null && assetUri.getPath().startsWith("/assets/")) {
                return assetUri.getPath();
            }
        } catch (Exception ignored) {
            return null;
        }
        return null;
    }

    private int effectivePort(URI uri) {
        if (uri.getPort() >= 0) {
            return uri.getPort();
        }
        if ("https".equalsIgnoreCase(uri.getScheme())) {
            return 443;
        }
        return 80;
    }

    private String resolveExtension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        if (dot <= 0 || dot == fileName.length() - 1) {
            return ".png";
        }
        String extension = fileName.substring(dot).toLowerCase(Locale.ROOT);
        return extension.matches("\\.[a-z0-9]{2,5}") ? extension : ".png";
    }

    private int countImages(@Nullable List<MultipartFile> images, @Nullable List<String> imageUrls) {
        int files = 0;
        if (images != null) {
            for (MultipartFile image : images) {
                if (image != null && !image.isEmpty()) {
                    files++;
                }
            }
        }

        int urls = 0;
        if (imageUrls != null) {
            for (String imageUrl : imageUrls) {
                if (imageUrl != null && !imageUrl.isBlank()) {
                    urls++;
                }
            }
        }

        return files + urls;
    }
}
