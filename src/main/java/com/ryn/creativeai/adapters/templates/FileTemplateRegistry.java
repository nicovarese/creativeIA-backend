package com.ryn.creativeai.adapters.templates;

import com.ryn.creativeai.api.exception.TemplateLoadingException;
import com.ryn.creativeai.core.ports.TemplateRegistryPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

@Slf4j
@Component
@RequiredArgsConstructor
public class FileTemplateRegistry implements TemplateRegistryPort {

    private final ResourceLoader resourceLoader;

    // configurable desde application.properties (con defaults)
    @Value("${creativeai.templates.base-path:templates/}")
    private String basePath;

    @Value("${creativeai.templates.fail-on-missing:true}")
    private boolean failOnMissing;

    /**
     * Lee un recurso del classpath y devuelve su contenido como String.
     * Ejemplo de path: "workflows/flux_simple_v1.json" (NO incluir 'templates/')
     */
    public String readClasspath(String path) {
        String normalized = normalize(path);
        Resource r = load(normalized);
        assertExistsOrFail(normalized, r);

        try (InputStream in = r.getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new TemplateLoadingException(
                    "No se pudo leer el recurso: " + location(normalized), e);
        }
    }

    /**
     * Igual que readClasspath, pero devuelve null si no existe (según flag).
     */
    public String readClasspathOrNull(String path) {
        String normalized = normalize(path);
        Resource r = load(normalized);

        if (!r.exists() || !r.isReadable()) {
            if (failOnMissing) {
                throw new TemplateLoadingException(
                        "Recurso no encontrado: " + location(normalized));
            }
            log.warn("Recurso no encontrado (devolviendo null): {}", location(normalized));
            return null;
        }

        try (InputStream in = r.getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            if (failOnMissing) {
                throw new TemplateLoadingException(
                        "No se pudo leer el recurso: " + location(normalized), e);
            }
            log.warn("Error leyendo recurso (devolviendo null): {}. Causa: {}", location(normalized), e.toString());
            return null;
        }
    }

    // ---------- helpers ----------

    private String normalize(String path) {
        if (path == null || path.isBlank()) {
            throw new TemplateLoadingException("El path de recurso está vacío o es null");
        }
        // normaliza separadores de Windows y quita '/' inicial
        String p = path.replace('\\', '/');
        if (p.startsWith("/")) p = p.substring(1);

        // asegura que basePath termine en '/'
        String base = basePath == null ? "templates/" : basePath;
        base = base.replace('\\', '/');
        if (!base.endsWith("/")) base = base + "/";

        return base + p; // p.ej.: templates/workflows/flux_simple_v1.json
    }

    private Resource load(String normalizedPath) {
        // classpath:templates/workflows/flux_simple_v1.json
        return resourceLoader.getResource("classpath:" + normalizedPath);
    }

    private void assertExistsOrFail(String normalized, Resource r) {
        if (!r.exists() || !r.isReadable()) {
            throw new TemplateLoadingException("Recurso no encontrado o no legible: " + location(normalized));
        }
    }

    private String location(String normalized) {
        return "classpath:" + normalized;
    }
}
