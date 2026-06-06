package com.ryn.creativeai.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;
import java.nio.file.Paths;

@Configuration
public class AssetsWebConfig implements WebMvcConfigurer {

    @Value("${storage.assetsDir:./assets}")
    private String assetsDir;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        Path absoluteAssetsPath = Paths.get(assetsDir).toAbsolutePath().normalize();
        String location = absoluteAssetsPath.toUri().toString();
        registry.addResourceHandler("/assets/**")
                .addResourceLocations(location);
    }
}
