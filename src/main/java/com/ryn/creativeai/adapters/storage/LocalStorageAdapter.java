package com.ryn.creativeai.adapters.storage;

import com.ryn.creativeai.core.ports.StoragePort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Slf4j
@Component
public class LocalStorageAdapter implements StoragePort {

    @Value("${storage.assetsDir:./assets}")
    String assetsDir;

    @Override
    public List<StoredImage> store(List<String> localPaths) {
        try {
            Path dir = Path.of(assetsDir).toAbsolutePath().normalize();
            Files.createDirectories(dir);

            return localPaths.stream().map(localPath -> {
                try {
                    Path src = Path.of(localPath).toAbsolutePath().normalize();
                    String originalName = src.getFileName().toString();
                    String safeName = sanitizeFileName(originalName);
                    String finalName = UUID.randomUUID() + "-" + safeName;
                    Path dst = dir.resolve(finalName);

                    Files.copy(src, dst, StandardCopyOption.REPLACE_EXISTING);

                    BufferedImage image = ImageIO.read(dst.toFile());
                    Integer width = image != null ? image.getWidth() : null;
                    Integer height = image != null ? image.getHeight() : null;

                    return new StoredImage("/assets/" + finalName, safeName, width, height, mimeFor(finalName));
                } catch (Exception ex) {
                    throw new IllegalStateException("No se pudo almacenar el asset generado", ex);
                }
            }).toList();
        } catch (Exception e) {
            throw new RuntimeException("No se pudo inicializar el directorio de assets", e);
        }
    }

    @Override
    public void delete(String url) {
        if (url == null || url.isBlank()) {
            return;
        }

        String filename = Paths.get(url).getFileName().toString();
        Path target = Paths.get(assetsDir).toAbsolutePath().normalize().resolve(filename);
        try {
            Files.deleteIfExists(target);
        } catch (Exception e) {
            log.warn("No se pudo borrar archivo {}: {}", target, e.toString());
        }
    }

    private static String mimeFor(String name) {
        if (name == null) {
            return "application/octet-stream";
        }

        String n = name.toLowerCase(Locale.ROOT);
        if (n.endsWith(".png")) return "image/png";
        if (n.endsWith(".jpg") || n.endsWith(".jpeg")) return "image/jpeg";
        if (n.endsWith(".webp")) return "image/webp";
        if (n.endsWith(".gif")) return "image/gif";
        if (n.endsWith(".bmp")) return "image/bmp";
        if (n.endsWith(".mp4")) return "video/mp4";
        if (n.endsWith(".webm")) return "video/webm";
        if (n.endsWith(".mov")) return "video/quicktime";
        return "application/octet-stream";
    }

    private String sanitizeFileName(String rawName) {
        String lower = rawName == null ? "asset.png" : rawName.toLowerCase(Locale.ROOT);
        String normalized = lower.replaceAll("[^a-z0-9._-]", "-");
        if (normalized.isBlank()) {
            return "asset.png";
        }
        return normalized;
    }
}
