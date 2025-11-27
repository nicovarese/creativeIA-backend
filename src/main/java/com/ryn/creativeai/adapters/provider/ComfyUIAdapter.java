package com.ryn.creativeai.adapters.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ryn.creativeai.core.ports.ImageProviderPort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.lang.Nullable;
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
    private final Path comfyInputDir;

    public ComfyUIAdapter(
            @Value("${comfy.baseUrl:http://127.0.0.1:8188}") String baseUrl,
            @Value("${comfy.downloadDir:./tmp/comfy}") String downloadDir,
            // si corres Comfy en Docker, seteá el path montado (p.ej. /opt/comfy/ComfyUI/input)
            @Value("${comfy.inputDir:./ComfyUI/input}") String comfyInputDir
    ) {
        this.baseUrl = baseUrl.replaceAll("/+$", "");
        this.downloadDir = Paths.get(downloadDir).toAbsolutePath();
        this.comfyInputDir = Paths.get(comfyInputDir).toAbsolutePath();
        try {
            Files.createDirectories(this.downloadDir);
            Files.createDirectories(this.comfyInputDir);
        } catch (Exception ignored) {}

        this.http = WebClient.builder()
                .baseUrl(this.baseUrl)
                .codecs(c -> c.defaultCodecs().maxInMemorySize(128 * 1024 * 1024)) // 128MB por si vuelven PNG grandes
                .build();
    }

    /** Extensiones válidas para imágenes */
    private static final Set<String> IMG_EXT = Set.of(".png", ".jpg", ".jpeg", ".webp", ".bmp", ".gif");

    private boolean isImageFilename(String s) {
        if (s == null || s.isBlank()) return false;
        String v = s.trim().toLowerCase(Locale.ROOT);
        int q = v.indexOf('?');
        if (q >= 0) v = v.substring(0, q);
        for (String ext : IMG_EXT) if (v.endsWith(ext)) return true;
        return false;
    }

    private String resolveExt(@Nullable String sourceName, @Nullable String contentType, @Nullable byte[] head) {
        if (sourceName != null) {
            String n = sourceName.toLowerCase(Locale.ROOT);
            int q = n.indexOf('?');
            if (q >= 0) n = n.substring(0, q);
            for (String ext : IMG_EXT) if (n.endsWith(ext)) return n.substring(n.lastIndexOf('.'));
        }
        if (contentType != null) {
            String ct = contentType.toLowerCase(Locale.ROOT);
            if (ct.contains("png")) return ".png";
            if (ct.contains("jpeg") || ct.contains("jpg")) return ".jpg";
            if (ct.contains("webp")) return ".webp";
            if (ct.contains("bmp")) return ".bmp";
            if (ct.contains("gif")) return ".gif";
        }
        if (head != null && head.length >= 12) {
            if (head[0]==(byte)0x89 && head[1]==0x50 && head[2]==0x4E && head[3]==0x47) return ".png"; // PNG
            if (head[0]==(byte)0xFF && head[1]==(byte)0xD8) return ".jpg"; // JPEG
            if (head[0]=='R' && head[1]=='I' && head[2]=='F' && head[3]=='F' && head[8]=='W' && head[9]=='E' && head[10]=='B' && head[11]=='P')
                return ".webp";
            if (head[0]=='B' && head[1]=='M') return ".bmp";
        }
        return ".png"; // fallback
    }

    private Path materializeToLocal(String src) throws Exception {
        if (src.startsWith("http://") || src.startsWith("https://")) {
            // Descargo bytes + best-effort content-type
            var resp = WebClient.create().get().uri(src).exchangeToMono(r ->
                    r.bodyToMono(byte[].class).map(b -> Map.entry(r.headers().contentType().orElse(null), b))
            ).block(Duration.ofSeconds(30));
            if (resp == null || resp.getValue().length == 0) {
                throw new IllegalStateException("No pude descargar imageUrl: " + src);
            }
            String ct = resp.getKey() == null ? null : resp.getKey().toString();
            byte[] bytes = resp.getValue();
            byte[] head = Arrays.copyOf(bytes, Math.min(bytes.length, 16));
            String ext = resolveExt(src, ct, head);
            Path tmp = Files.createTempFile("creativeai_in_", ext);
            Files.write(tmp, bytes, StandardOpenOption.TRUNCATE_EXISTING);
            return tmp;
        } else {
            Path p = Paths.get(src);
            return p.isAbsolute() ? p : p.toAbsolutePath();
        }
    }

    private String copyIntoComfyInput(Path local) throws Exception {
        String name = local.getFileName().toString();
        String ext = name.contains(".") ? name.substring(name.lastIndexOf('.')).toLowerCase(Locale.ROOT) : ".png";
        if (!IMG_EXT.contains(ext)) ext = ".png";
        String filename = UUID.randomUUID().toString().replace("-", "") + ext;
        Files.copy(local, comfyInputDir.resolve(filename), StandardCopyOption.REPLACE_EXISTING);
        return filename;
    }

    /** Reescribe inputs de imagen (string o arrays) a filename dentro de ComfyUI/input */
    private void rewriteImageInputs(ObjectNode workflow) throws Exception {
        Iterator<String> it = workflow.fieldNames();
        while (it.hasNext()) {
            String nodeId = it.next();
            JsonNode node = workflow.get(nodeId);
            if (node == null || !node.isObject()) continue;

            JsonNode inputs = node.get("inputs");
            if (inputs == null || !inputs.isObject()) continue;

            // 1) Caso directo: campos de texto que parecen imagen
            Iterator<String> in = inputs.fieldNames();
            List<Runnable> writers = new ArrayList<>();
            while (in.hasNext()) {
                String field = in.next();
                JsonNode val = inputs.get(field);
                if (val == null) continue;

                if (val.isTextual() && isImageFilename(val.asText())) {
                    String original = val.asText();
                    String filename = normalizeToComfyFilename(original);
                    writers.add(() -> ((ObjectNode) inputs).put(field, filename));
                } else if (val.isArray()) {
                    boolean touched = false;
                    List<String> newVals = new ArrayList<>();
                    for (JsonNode v : val) {
                        if (v.isTextual() && isImageFilename(v.asText())) {
                            try {
                                newVals.add(normalizeToComfyFilename(v.asText()));
                                touched = true;
                            } catch (Exception e) {
                                // si falla uno, dejamos el original de ese item
                                newVals.add(v.asText());
                            }
                        } else if (v.isTextual()) {
                            newVals.add(v.asText());
                        }
                    }
                    if (touched) {
                        writers.add(() -> {
                            var arr = ((ObjectNode) inputs).putArray(field);
                            newVals.forEach(arr::add);
                        });
                    }
                }
            }
            // aplico writes fuera del loop de iteradores
            writers.forEach(Runnable::run);
        }
    }

    private String normalizeToComfyFilename(String value) throws Exception {
        Path local = materializeToLocal(value);
        return copyIntoComfyInput(local);
    }

    /* ================== API ================== */

    @Override
    public List<String> generate(String compiledWorkflowJson) throws Exception {
        // 1) Parseo y reescribo inputs de imagen a filename dentro de ComfyUI/input
        final ObjectNode workflow = (ObjectNode) M.readTree(compiledWorkflowJson);
        rewriteImageInputs(workflow);

        // 2) Wrap: Comfy espera { "prompt": <workflow-json>, "client_id": "<id>" }
        final ObjectNode body = M.createObjectNode();
        body.set("prompt", workflow);
        final String clientId = UUID.randomUUID().toString();
        body.put("client_id", clientId);

        // 3) POST /prompt → prompt_id
        final JsonNode submit = http.post()
                .uri("/prompt")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block(Duration.ofSeconds(20));

        if (submit == null || submit.get("prompt_id") == null) {
            String err = (submit != null && submit.has("error")) ? submit.get("error").toString() : "respuesta nula";
            throw new IllegalStateException("ComfyUI no devolvió prompt_id. Detalle: " + err);
        }
        if (submit.has("node_errors") && submit.get("node_errors").size() > 0) {
            throw new IllegalStateException("ComfyUI rechazó el prompt: " + submit.get("node_errors").toString());
        }

        final String promptId = submit.get("prompt_id").asText();
        final int number = submit.has("number") ? submit.get("number").asInt() : -1;
        System.out.println("[ComfyUI] prompt_id=" + promptId + " number=" + number + " client_id=" + clientId);

        // 4) Poll /history/{id}
        final JsonNode hist = waitForHistory(promptId, 300);
        if (hist == null) {
            throw new IllegalStateException("Timeout esperando resultados de ComfyUI (prompt_id=" + promptId + ")");
        }
        if (hist.has("status") && hist.get("status").has("error") && !hist.get("status").get("error").isNull()) {
            throw new IllegalStateException("Ejecución fallida: " + hist.get("status").get("error").toString());
        }
        if (hist.has("node_errors") && hist.get("node_errors").size() > 0) {
            throw new IllegalStateException("Errores de nodos: " + hist.get("node_errors").toString());
        }

        // 5) Extraer y descargar outputs
        final List<ImageRef> toDownload = extractFiles(hist);
        if (toDownload.isEmpty()) {
            throw new IllegalStateException("ComfyUI terminó sin archivos descargables (prompt_id=" + promptId + ")");
        }

        final List<String> localPaths = new ArrayList<>();
        for (ImageRef ref : toDownload) {
            final String path = downloadWithRetry(ref, 3, 400);
            localPaths.add(path);
        }
        System.out.println("[ComfyUI] descargados " + localPaths.size() + " archivos → " + this.downloadDir);
        return localPaths;
    }

    /* ----------------- helpers HTTP/History ----------------- */

    private JsonNode waitForHistory(String promptId, int timeoutSeconds) throws InterruptedException {
        final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(timeoutSeconds);
        while (System.nanoTime() < deadline) {
            final JsonNode hist = http.get()
                    .uri("/history/{id}", promptId)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .onErrorResume(e -> Mono.empty())
                    .block(Duration.ofSeconds(8));

            if (hist != null && hist.has(promptId)) {
                final JsonNode entry = hist.get(promptId);
                if (entry.has("outputs") && containsAnyFile(entry.get("outputs"))) return entry;
                if (entry.has("status") && entry.get("status").has("error") && !entry.get("status").get("error").isNull()) return entry;
                if (entry.has("status") && entry.get("status").has("completed") &&
                        entry.get("status").get("completed").asBoolean(false)) return entry;
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
                    (out.has("gifs")   && out.get("gifs").isArray()   && out.get("gifs").size() > 0) ||
                    (out.has("files")  && out.get("files").isArray()  && out.get("files").size() > 0)) {
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
