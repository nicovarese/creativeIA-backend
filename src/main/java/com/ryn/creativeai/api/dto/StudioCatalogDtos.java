package com.ryn.creativeai.api.dto;

import java.util.List;

public class StudioCatalogDtos {

    public record BrandOption(
            String name,
            List<String> products
    ) {}

    public record StudioCatalogResponse(
            List<String> styles,
            List<BrandOption> brands,
            List<String> mockupTemplates
    ) {}
}
