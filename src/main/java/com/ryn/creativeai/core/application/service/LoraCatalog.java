package com.ryn.creativeai.core.application.service;

import com.ryn.creativeai.core.domain.model.BrandLoraStatus;
import com.ryn.creativeai.infra.BrandLoraRepository;
import org.springframework.stereotype.Component;

import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Component
public class LoraCatalog {

    public record LoraSpec(String name, double strengthModel, double strengthClip) {
    }

    private final BrandLoraRepository brandLoraRepo;

    private final Map<String, Map<String, LoraSpec>> brandProduct = new HashMap<>();
    private final Map<String, LoraSpec> styles = new HashMap<>();

    public LoraCatalog(BrandLoraRepository brandLoraRepo) {
        this.brandLoraRepo = brandLoraRepo;

        addBrand("Hyundai", "Kona",   new LoraSpec("hyundai_kona_v1", 0.8, 0.8));
        addBrand("Hyundai", "Tucson", new LoraSpec("hyundai_tucson_v1", 0.75, 0.75));
        addBrand("Itau", "*",         new LoraSpec("itau_brand_v2", 0.65, 0.65));

        styles.put("Realismo", new LoraSpec("style_realism_v1", 0.6, 0.6));
        styles.put("Anime",    new LoraSpec("style_anime_v2",  0.7, 0.7));
    }

    private void addBrand(String b, String p, LoraSpec spec){
        brandProduct.computeIfAbsent(b.toLowerCase(), k->new HashMap<>()).put(p.toLowerCase(), spec);
    }

    /** Match contra catálogo hardcoded (seed). */
    public Optional<LoraSpec> brandProduct(String brand, String product){
        if (brand==null || brand.isBlank()) return Optional.empty();
        var m = brandProduct.getOrDefault(brand.toLowerCase(), Map.of());
        LoraSpec s = product!=null ? m.get(product.toLowerCase()) : null;
        if (s==null) s = m.get("*");
        return Optional.ofNullable(s);
    }

    /**
     * Resuelve brand mirando primero las BrandLoras COMPLETED del usuario;
     * si no hay match, cae al catálogo hardcoded.
     */
    public Optional<LoraSpec> brandProduct(String brand, String product, UUID ownerId) {
        if (brand == null || brand.isBlank()) return Optional.empty();
        if (ownerId != null) {
            var fromUser = brandLoraRepo.findByOwnerIdAndNameIgnoreCaseAndStatus(
                    ownerId, brand.trim(), BrandLoraStatus.COMPLETED);
            if (fromUser.isPresent()) {
                var bl = fromUser.get();
                String filename = bl.getSafetensorsPath() == null
                        ? bl.getName().toLowerCase()
                        : Paths.get(bl.getSafetensorsPath()).getFileName().toString();
                return Optional.of(new LoraSpec(filename, bl.getStrengthModel(), bl.getStrengthClip()));
            }
        }
        return brandProduct(brand, product);
    }

    public Optional<LoraSpec> style(String style){
        if (style==null || style.isBlank()) return Optional.empty();
        return Optional.ofNullable(styles.get(style));
    }
}
