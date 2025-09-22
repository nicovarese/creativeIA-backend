package com.ryn.creativeai.api.controller;

import com.ryn.creativeai.core.domain.model.Asset;
import com.ryn.creativeai.core.domain.model.Project;
import com.ryn.creativeai.infra.AssetRepository;
import com.ryn.creativeai.infra.ProjectRepository;
import jakarta.validation.Valid;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/v1/projects")
@RequiredArgsConstructor
public class ProjectsController {

    private final ProjectRepository projects;
    private final AssetRepository assets;

    /** Crear proyecto simple (name único) */
    @PostMapping
    public Project create(@Valid @RequestBody CreateProjectReq req) {
        Project p = new Project();
        p.setName(req.getName());
        p.setOwnerUserId(req.getOwnerUserId()); // opcional
        return projects.save(p);
    }

    /** Listar proyectos */
    @GetMapping
    public Page<Project> list(Pageable pageable) {
        return projects.findAll(pageable);
    }

    /** Assets de un proyecto (paginado + búsqueda) */
    @GetMapping("/{projectId}/assets")
    public Map<String, Object> listAssets(@PathVariable("projectId") UUID projectId,
                                          @RequestParam(defaultValue = "1") int page,
                                          @RequestParam(defaultValue = "24") int size,
                                          @RequestParam(required = false) String search) {
        page = Math.max(page, 1);
        size = Math.min(Math.max(size, 1), 100);

        Page<Asset> p = assets.search(projectId,
                (search == null || search.isBlank()) ? null : search,
                PageRequest.of(page - 1, size));

        return Map.of(
                "items", p.getContent().stream().map(a -> Map.of(
                        "id", a.getId(),
                        "url", a.getUrl(),
                        "flow", a.getFlow(),
                        "createdAt", a.getCreatedAt()
                )).toList(),
                "page", page,
                "size", size,
                "total", p.getTotalElements()
        );
    }

    @Data
    public static class CreateProjectReq {
        private String name;
        private String ownerUserId; // opcional
    }
}
