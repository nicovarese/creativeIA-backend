package com.ryn.creativeai.core.ports;

import java.util.List;

public interface StoragePort {
    record StoredImage(String url, String fileName, Integer w, Integer h, String mimeType) {}

    List<StoredImage> store(List<String> localPaths);

    void delete(String url);
}
