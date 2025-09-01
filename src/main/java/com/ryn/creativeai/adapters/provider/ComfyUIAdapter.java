package com.ryn.creativeai.adapters.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ryn.creativeai.core.ports.ImageProviderPort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Component("comfyui")
public class ComfyUIAdapter implements ImageProviderPort {

    private final WebClient http;
    private final ObjectMapper M = new ObjectMapper();
    private final String baseUrl;
    private final File downloadDir;

    public ComfyUIAdapter(
            @Value("${comfy.baseUrl:http://localhost:8188}") String baseUrl,
            @Value("${comfy.downloadDir:./tmp/comfy}") String downloadDir
    ) {
        this.baseUrl = baseUrl.replaceAll("/+$", "");
        this.downloadDir = new File(downloadDir);
        this.downloadDir.mkdirs();

        this.http = WebClient.builder()
                .baseUrl(this.baseUrl)
                .codecs(c -> c.defaultCodecs().maxInMemorySize(32 * 1024 * 1024)) // 32MB
                .build();
    }

    /**
     * Envia el workflow compilado a /prompt, espera la finalización via /history,
     * descarga las imágenes a disco y retorna los paths locales.
     */
    @Override
    public List<String> generate(String compiledWorkflowJson) throws Exception {
        // 1) Wrap: Comfy espera { "prompt": <workflow-json> }
        JsonNode workflow = M.readTree(compiledWorkflowJson);
        JsonNode body = M.createObjectNode().set("prompt", workflow);

        // 2) POST /prompt → prompt_id
        JsonNode submit = http.post()
                .uri("/prompt")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(M.writeValueAsString(body))
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block(Duration.ofSeconds(10));

        if (submit == null || submit.get("prompt_id") == null) {
            throw new IllegalStateException("ComfyUI no devolvió prompt_id");
        }
        String promptId = submit.get("prompt_id").asText();

        // 3) Poll /history/{id} hasta que tenga outputs o timeout
        JsonNode hist = waitForHistory(promptId, 400); // timeout 90s (ajustable)
        if (hist == null) {
            throw new IllegalStateException("Timeout esperando resultados de ComfyUI");
        }

        // 4) Extraer lista de imágenes a descargar
        List<ImageRef> toDownload = extractImages(hist);
        if (toDownload.isEmpty()) {
            throw new IllegalStateException("ComfyUI terminó sin imágenes");
        }

        // 5) Descargar imágenes → paths locales
        List<String> localPaths = new ArrayList<>();
        for (ImageRef ref : toDownload) {
            String path = downloadImage(ref);
            localPaths.add(path);
        }
        return localPaths;
    }

    /* ----------------- helpers ----------------- */

    private JsonNode waitForHistory(String promptId, int timeoutSeconds) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(timeoutSeconds);
        while (System.nanoTime() < deadline) {
            JsonNode hist = http.get()
                    .uri("/history/{id}", promptId)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .onErrorResume(e -> Mono.empty())
                    .block(Duration.ofSeconds(5));

            // /history/{id} devuelve un objeto con la key = promptId
            if (hist != null && hist.has(promptId)) {
                JsonNode entry = hist.get(promptId);
                // Heurística: si hay "outputs" con al menos un nodo que tenga "images"
                if (entry.has("outputs")) {
                    JsonNode outputs = entry.get("outputs");
                    if (containsAnyImage(outputs)) return entry;
                }
                // También puede venir "status": {"completed": true}
                if (entry.has("status") && entry.get("status").has("completed") &&
                        entry.get("status").get("completed").asBoolean(false)) {
                    return entry; // aunque no haya images, lo devolvemos para que falle arriba
                }
            }
            Thread.sleep(1000);
        }
        return null;
    }

    private boolean containsAnyImage(JsonNode outputs) {
        if (outputs == null || !outputs.isObject()) return false;
        Iterator<String> it = outputs.fieldNames();
        while (it.hasNext()) {
            String node = it.next();
            JsonNode out = outputs.get(node);
            if (out != null && out.has("images") && out.get("images").isArray() && out.get("images").size() > 0) {
                return true;
            }
        }
        return false;
    }

    /**
     * Estructura típica de /history: outputs -> { nodeId: { images: [ {filename, subfolder, type} ] } }
     */
    private List<ImageRef> extractImages(JsonNode historyEntry) {
        List<ImageRef> out = new ArrayList<>();
        if (!historyEntry.has("outputs")) return out;
        JsonNode outputs = historyEntry.get("outputs");
        Iterator<String> it = outputs.fieldNames();
        while (it.hasNext()) {
            String node = it.next();
            JsonNode outNode = outputs.get(node);
            if (outNode != null && outNode.has("images")) {
                for (JsonNode img : outNode.get("images")) {
                    String filename = optText(img, "filename");
                    String subfolder = optText(img, "subfolder"); // puede venir "" si es raíz
                    String type = optText(img, "type");           // normalmente "output"
                    if (filename != null) {
                        out.add(new ImageRef(filename, subfolder, (type == null || type.isBlank()) ? "output" : type));
                    }
                }
            }
        }
        return out;
    }

    private String downloadImage(ImageRef ref) throws Exception {
        // Comfy: /view?filename=...&subfolder=...&type=output
        String uri = String.format("/view?filename=%s&subfolder=%s&type=%s",
                urlEncode(ref.filename()), urlEncode(ref.subfolder()), urlEncode(ref.type()));

        byte[] bytes = http.get()
                .uri(uri)
                .retrieve()
                .bodyToMono(byte[].class)
                .block(Duration.ofSeconds(20));

        if (bytes == null || bytes.length == 0) {
            throw new IllegalStateException("No se pudo descargar imagen: " + ref.filename());
        }

        // Guardar en disco
        String safeName = ref.filename().replaceAll("[^a-zA-Z0-9._-]", "_");
        File dst = new File(downloadDir, safeName);
        try (FileOutputStream os = new FileOutputStream(dst)) {
            os.write(bytes);
        }
        return dst.getAbsolutePath();
    }

    private static String optText(JsonNode n, String field) {
        return (n != null && n.has(field) && !n.get(field).isNull()) ? n.get(field).asText() : null;
    }

    private static String urlEncode(String s) {
        if (s == null) return "";
        return java.net.URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    private record ImageRef(String filename, String subfolder, String type) {
    }
}
