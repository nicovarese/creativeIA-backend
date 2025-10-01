package com.ryn.creativeai.adapters.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ryn.creativeai.core.ports.ImageProviderPort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Component("comfyui")
public class ComfyUIAdapter implements ImageProviderPort {

    private final WebClient http;
    private final ObjectMapper M = new ObjectMapper();
    private final String baseUrl;
    private final Path downloadDir;

    public ComfyUIAdapter(
            // 👈 por defecto 127.0.0.1:8188 como mostrás en tu consola
            @Value("${comfy.baseUrl:http://127.0.0.1:8188}") String baseUrl,
            @Value("${comfy.downloadDir:./tmp/comfy}") String downloadDir
    ) {
        this.baseUrl = baseUrl.replaceAll("/+$", "");
        this.downloadDir = Paths.get(downloadDir).toAbsolutePath();
        try { Files.createDirectories(this.downloadDir); } catch (Exception ignored) {}

        this.http = WebClient.builder()
                .baseUrl(this.baseUrl)
                .codecs(c -> c.defaultCodecs().maxInMemorySize(128 * 1024 * 1024)) // 128MB por si vuelven PNG grandes
                .build();
    }

    /**
     * Envía el workflow compilado a /prompt, espera la finalización via /history/{id},
     * descarga las imágenes a disco y retorna los paths locales.
     */
    @Override
    public List<String> generate(String compiledWorkflowJson) throws Exception {
        // 1) Wrap: Comfy espera { "prompt": <workflow-json>, "client_id": "<id>" }
        final JsonNode workflow = M.readTree(compiledWorkflowJson);
        final ObjectNode body = M.createObjectNode();
        body.set("prompt", workflow);
        final String clientId = UUID.randomUUID().toString();
        body.put("client_id", clientId);

        // 2) POST /prompt → prompt_id (+ revisar posibles node_errors en respuesta)
        final JsonNode submit = http.post()
                .uri("/prompt")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)                  // 👈 mando el JsonNode directo
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block(Duration.ofSeconds(20));

        if (submit == null || submit.get("prompt_id") == null) {
            String err = (submit != null && submit.has("error")) ? submit.get("error").toString() : "respuesta nula";
            throw new IllegalStateException("ComfyUI no devolvió prompt_id. Detalle: " + err);
        }

        // Si vienen errores de validación de entrada, los muestro ahora
        if (submit.has("node_errors") && submit.get("node_errors").size() > 0) {
            throw new IllegalStateException("ComfyUI rechazó el prompt: " + submit.get("node_errors").toString());
        }

        final String promptId = submit.get("prompt_id").asText();
        // opcional: número de tareas encoladas
        final int number = submit.has("number") ? submit.get("number").asInt() : -1;
        System.out.println("[ComfyUI] prompt_id=" + promptId + " number=" + number + " client_id=" + clientId);

        // 3) Poll /history/{id} hasta outputs|status.completed|error
        final JsonNode hist = waitForHistory(promptId, 300 /* seg */); // 5 minutos, ajustable
        if (hist == null) {
            throw new IllegalStateException("Timeout esperando resultados de ComfyUI (prompt_id=" + promptId + ")");
        }

        // Errores durante ejecución
        if (hist.has("status") && hist.get("status").has("error") && !hist.get("status").get("error").isNull()) {
            throw new IllegalStateException("Ejecución fallida: " + hist.get("status").get("error").toString());
        }
        if (hist.has("node_errors") && hist.get("node_errors").size() > 0) {
            throw new IllegalStateException("Errores de nodos: " + hist.get("node_errors").toString());
        }

        // 4) Extraer lista de archivos a descargar (images, gifs o files)
        final List<ImageRef> toDownload = extractFiles(hist);
        if (toDownload.isEmpty()) {
            throw new IllegalStateException("ComfyUI terminó sin archivos descargables (prompt_id=" + promptId + ")");
        }

        // 5) Descargar archivos → paths locales (con reintento corto)
        final List<String> localPaths = new ArrayList<>();
        for (ImageRef ref : toDownload) {
            final String path = downloadWithRetry(ref, 3, 400);
            localPaths.add(path);
        }
        System.out.println("[ComfyUI] descargados " + localPaths.size() + " archivos → " + this.downloadDir);
        return localPaths;
    }

    /* ----------------- helpers ----------------- */

    private JsonNode waitForHistory(String promptId, int timeoutSeconds) throws InterruptedException {
        final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(timeoutSeconds);
        while (System.nanoTime() < deadline) {
            final JsonNode hist = http.get()
                    .uri("/history/{id}", promptId)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .onErrorResume(e -> Mono.empty())
                    .block(Duration.ofSeconds(8));

            // /history/{id} devuelve { "<id>": {...entry...} }
            if (hist != null && hist.has(promptId)) {
                final JsonNode entry = hist.get(promptId);

                // si ya hay outputs con imágenes/archivos → listo
                if (entry.has("outputs") && containsAnyFile(entry.get("outputs"))) return entry;

                // si hay error explícito → devolvelo (el caller lo interpretará)
                if (entry.has("status") && entry.get("status").has("error") && !entry.get("status").get("error").isNull()) {
                    return entry;
                }
                // si dice completed true → devolvelo (aunque no tenga imágenes)
                if (entry.has("status") && entry.get("status").has("completed") &&
                        entry.get("status").get("completed").asBoolean(false)) {
                    return entry;
                }
            }
            Thread.sleep(900);
        }
        return null;
    }

    private boolean containsAnyFile(JsonNode outputs) {
        if (outputs == null || !outputs.isObject()) return false;
        final Iterator<String> it = outputs.fieldNames();
        while (it.hasNext()) {
            final JsonNode out = outputs.get(it.next());
            if (out == null) continue;
            if ((out.has("images") && out.get("images").isArray() && out.get("images").size() > 0) ||
                    (out.has("gifs")    && out.get("gifs").isArray()    && out.get("gifs").size() > 0) ||
                    (out.has("files")   && out.get("files").isArray()   && out.get("files").size() > 0)) {
                return true;
            }
        }
        return false;
    }

    /** outputs -> { nodeId: { images|gifs|files: [ {filename, subfolder, type} ] } } */
    private List<ImageRef> extractFiles(JsonNode historyEntry) {
        final List<ImageRef> out = new ArrayList<>();
        if (!historyEntry.has("outputs")) return out;
        final JsonNode outputs = historyEntry.get("outputs");
        final Iterator<String> it = outputs.fieldNames();
        while (it.hasNext()) {
            final JsonNode nodeOut = outputs.get(it.next());
            if (nodeOut == null) continue;
            collectArray(nodeOut, "images", out);
            collectArray(nodeOut, "gifs", out);
            collectArray(nodeOut, "files", out);
        }
        return out;
    }

    private void collectArray(JsonNode nodeOut, String arrayField, List<ImageRef> acc) {
        if (nodeOut.has(arrayField) && nodeOut.get(arrayField).isArray()) {
            for (JsonNode f : nodeOut.get(arrayField)) {
                final String filename = optText(f, "filename");
                if (filename == null || filename.isBlank()) continue;
                final String subfolder = Optional.ofNullable(optText(f, "subfolder")).orElse("");
                final String type = Optional.ofNullable(optText(f, "type")).filter(s -> !s.isBlank()).orElse("output");
                acc.add(new ImageRef(filename, subfolder, type));
            }
        }
    }

    private String downloadWithRetry(ImageRef ref, int retries, long backoffMs) throws Exception {
        Exception last = null;
        for (int i = 0; i <= retries; i++) {
            try {
                return downloadOne(ref);
            } catch (Exception ex) {
                last = ex;
                Thread.sleep(backoffMs);
            }
        }
        throw last;
    }

    private String downloadOne(ImageRef ref) throws Exception {
        final String uri = String.format(
                "/view?filename=%s&subfolder=%s&type=%s",
                enc(ref.filename()), enc(ref.subfolder()), enc(ref.type())
        );

        final byte[] bytes = http.get()
                .uri(uri)
                .retrieve()
                .bodyToMono(byte[].class)
                .block(Duration.ofSeconds(20));

        if (bytes == null || bytes.length == 0) {
            throw new IllegalStateException("No se pudo descargar: " + ref);
        }

        final String safe = ref.filename().replaceAll("[^a-zA-Z0-9._-]", "_");
        final Path dst = downloadDir.resolve(safe);
        Files.createDirectories(dst.getParent());
        Files.write(dst, bytes, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        return dst.toString();
    }

    private static String optText(JsonNode n, String field) {
        return (n != null && n.has(field) && !n.get(field).isNull()) ? n.get(field).asText() : null;
    }
    private static String enc(String s) {
        return s == null ? "" : URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    private record ImageRef(String filename, String subfolder, String type) {}
}
