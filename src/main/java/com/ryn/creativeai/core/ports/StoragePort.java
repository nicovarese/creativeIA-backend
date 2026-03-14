package com.ryn.creativeai.core.ports;

import java.util.List;

public interface StoragePort {
    record StoredImage(String url, String fileName, Integer w, Integer h) {}
    List<StoredImage> store(List<String> localPaths);
}
