package com.ryn.creativeai.api.controller;

import com.ryn.creativeai.api.dto.StudioCatalogDtos.BrandOption;
import com.ryn.creativeai.api.dto.StudioCatalogDtos.StudioCatalogResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/v1/catalog")
public class StudioCatalogController {

    @GetMapping("/studio-options")
    public StudioCatalogResponse studioOptions() {
        return new StudioCatalogResponse(
                List.of("Ninguno", "Realismo", "Animacion", "Classic"),
                List.of(
                        new BrandOption("Ninguno", List.of("Ninguno")),
                        new BrandOption("Hyundai", List.of("Ninguno", "Kona", "Tucson", "Elantra", "Santa Fe")),
                        new BrandOption("Itau", List.of("Ninguno", "Cuenta", "Tarjeta", "Prestamo")),
                        new BrandOption("Marca ejemplo", List.of("Ninguno", "Producto A", "Producto B"))
                ),
                List.of("Remera", "Cartel", "Mockup iPhone", "Lona")
        );
    }
}
