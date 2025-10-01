package com.ryn.creativeai.core.application.service;

import lombok.Getter;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Component
public class LoraCatalog {


    public record LoraSpec(String name, double strengthModel, double strengthClip) {
    }

    private final Map<String, Map<String, LoraSpec>> brandProduct = new HashMap<>();
    private final Map<String, LoraSpec> styles = new HashMap<>();

    public LoraCatalog() {
        addBrand("Hyundai", "Kona",   new LoraSpec("hyundai_kona_v1", 0.8, 0.8));
        addBrand("Hyundai", "Tucson", new LoraSpec("hyundai_tucson_v1", 0.75, 0.75));
        addBrand("Itau", "*",         new LoraSpec("itau_brand_v2", 0.65, 0.65));

        styles.put("Realismo", new LoraSpec("style_realism_v1", 0.6, 0.6));
        styles.put("Anime",    new LoraSpec("style_anime_v2",  0.7, 0.7));
    }

    private void addBrand(String b, String p, LoraSpec spec){
        brandProduct.computeIfAbsent(b.toLowerCase(), k->new HashMap<>()).put(p.toLowerCase(), spec);
    }

    public Optional<LoraSpec> brandProduct(String brand, String product){
        if (brand==null || brand.isBlank()) return Optional.empty();
        var m = brandProduct.getOrDefault(brand.toLowerCase(), Map.of());
        LoraSpec s = product!=null ? m.get(product.toLowerCase()) : null;
        if (s==null) s = m.get("*");
        return Optional.ofNullable(s);
    }

    public Optional<LoraSpec> style(String style){
        if (style==null || style.isBlank()) return Optional.empty();
        return Optional.ofNullable(styles.get(style));
    }
}
