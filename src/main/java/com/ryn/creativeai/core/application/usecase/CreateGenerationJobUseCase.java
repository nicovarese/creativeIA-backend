package com.ryn.creativeai.core.application.usecase;

import com.ryn.creativeai.core.application.service.ParamResolver;
import com.ryn.creativeai.core.application.service.TemplateCompiler;
import com.ryn.creativeai.core.domain.model.Asset;
import com.ryn.creativeai.core.domain.model.Job;
import com.ryn.creativeai.core.ports.ImageProviderPort;
import com.ryn.creativeai.core.ports.StoragePort;
import com.ryn.creativeai.core.ports.TemplateRegistryPort;
import com.ryn.creativeai.infra.AssetRepository;
import com.ryn.creativeai.infra.JobRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * Caso de uso: crea un Job de generación de imagen.
 * Flujo:
 * 1) Carga el workflow (JSON) y su schema (JSON) desde el TemplateRegistry.
 * 2) Resuelve/valida parámetros contra el schema (defaults + checks).
 * 3) Compila el JSON del workflow reemplazando placeholders.
 * 4) Persiste Job en DB con estado QUEUED y lanza el procesamiento async.
 * Invariante: nunca envía al proveedor si el schema no valida.
 */
@Service
@RequiredArgsConstructor
public class CreateGenerationJobUseCase {

    private final TemplateRegistryPort registry;
    private final ParamResolver paramResolver;
    private final TemplateCompiler templateCompiler;
    private final Map<String, ImageProviderPort> providers;
    private final StoragePort storage;
    private final JobRepository jobs;
    private final AssetRepository assets;

    public record Command(String templateKey, String version, String provider, Map<String, Object> params) {}

    // --- record interno para no depender de un método load inexistente en el puerto
    private record TemplateDef(String json, String schemaJson) {}

    public UUID handle(Command cmd) {
        // Carga directa usando lo que YA existe en el registry (readClasspath)
        var def = loadFromClasspath(cmd.templateKey(), cmd.version());

        var finalParams = paramResolver.resolve(def.schemaJson(), cmd.params());
        var compiled = templateCompiler.compile(def.json(), finalParams);

        var job = new Job();
        job.setTemplateKey(cmd.templateKey());
        job.setTemplateVer(cmd.version());
        job.setProvider(cmd.provider());
        job.setCompiledJson(compiled);
        job.markQueued();
        jobs.save(job);

        CompletableFuture.runAsync(() -> process(job.getId()));
        return job.getId();
    }

    private TemplateDef loadFromClasspath(String key, String ver) {
        // Convención de nombres que ya venías usando:
        // templates/workflows/{key}_{ver}.json
        // templates/schemas/{key}_{ver}.schema.json
        var base = key + "_" + ver;

        var workflowJson = registry.readClasspath("workflows/" + base + ".json");
        var schemaJson   = registry.readClasspath("schemas/" + base + ".schema.json");

        return new TemplateDef(workflowJson, schemaJson);
    }

    private void process(UUID jobId) {
        var job = jobs.findById(jobId).orElseThrow();
        job.markRunning();
        jobs.save(job);

        var provider = providers.get(job.getProvider());
        if (provider == null) throw new IllegalArgumentException("Provider desconocido: " + job.getProvider());

        try {
            var localPaths = provider.generate(job.getCompiledJson());
            var storedImages = storage.store(localPaths);

            for (var si : storedImages) {
                var asset = new Asset();
                asset.setJob(job);
                asset.setUrl(si.url());
                asset.setWidth(si.w());
                asset.setHeight(si.h());
                assets.save(asset);
            }
            job.markDone();
        } catch (Exception e) {
            job.markFailed(e.getMessage());
        } finally {
            jobs.save(job);
        }
    }
}
