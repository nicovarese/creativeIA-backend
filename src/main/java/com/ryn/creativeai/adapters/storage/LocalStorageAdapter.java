package com.ryn.creativeai.adapters.storage;

import com.ryn.creativeai.core.ports.StoragePort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

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

                    return new StoredImage("/assets/" + finalName, safeName, width, height);
                } catch (Exception ex) {
                    throw new IllegalStateException("No se pudo almacenar el asset generado", ex);
                }
            }).toList();
        } catch (Exception e) {
            throw new RuntimeException("No se pudo inicializar el directorio de assets", e);
        }
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
