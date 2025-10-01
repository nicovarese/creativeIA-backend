package com.ryn.creativeai.adapters.storage;

import com.ryn.creativeai.core.ports.StoragePort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;
import java.nio.file.Files;
import java.util.List;


/**
 * Implementación de StoragePort que copia imágenes a ./assets y devuelve URLs servibles.
 * Nota: en prod conviene S3/MinIO + CDN.
 */
@Component
public class LocalStorageAdapter implements StoragePort {

    @Value("${storage.assetsDir:./assets}")
    String assetsDir;

    @Override
    public List<StoredImage> store(List<String> localPaths) {
        try {
            File dir = new File(assetsDir);
            if (!dir.exists() && !dir.mkdirs()) {
                throw new RuntimeException("No se pudo crear el directorio: " + assetsDir);
            }

            return localPaths.stream().map(p -> {
                File src = new File(p);
                File dst = new File(assetsDir, src.getName());
                try {
                    Files.copy(src.toPath(), dst.toPath());
                } catch (Exception ignored) {
                }
                return new StoredImage("/assets/" + dst.getName(), null, null);
            }).toList();

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}

