package com.ryn.creativeai.api.controller;

import com.ryn.creativeai.api.dto.ProjectDtos.CreateProjectRequest;
import com.ryn.creativeai.api.dto.ProjectDtos.ProjectAssetResponse;
import com.ryn.creativeai.api.dto.ProjectDtos.ProjectAssetsPage;
import com.ryn.creativeai.api.dto.ProjectDtos.ProjectResponse;
import com.ryn.creativeai.core.domain.model.Asset;
import com.ryn.creativeai.core.domain.model.Project;
import com.ryn.creativeai.core.ports.StoragePort;
import com.ryn.creativeai.infra.AssetRepository;
import com.ryn.creativeai.infra.ProjectRepository;
import com.ryn.creativeai.security.CurrentUserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@RestController
@RequestMapping("/v1/projects")
@RequiredArgsConstructor
public class ProjectsController {

    private final ProjectRepository projects;
    private final AssetRepository assets;
    private final CurrentUserService currentUser;
    private final StoragePort storage;

    @PostMapping
    public ProjectResponse create(@Valid @RequestBody CreateProjectRequest req) {
        var user = currentUser.requireUser();
        String cleanName = req.name().trim();

        if (projects.existsByOwnerIdAndNameIgnoreCase(user.getId(), cleanName)) {
            throw new ResponseStatusException(CONFLICT, "Ya existe un proyecto con ese nombre");
        }

        Project project = new Project();
        project.setName(cleanName);
        project.setOwner(user);
        return toResponse(projects.save(project));
    }

    @GetMapping
    public Page<ProjectResponse> list(Pageable pageable) {
        var user = currentUser.requireUser();
        Pageable effectivePageable = pageable.getSort().isSorted()
                ? pageable
                : PageRequest.of(
                        pageable.getPageNumber(),
                        pageable.getPageSize(),
                        Sort.by(Sort.Direction.DESC, "updatedAt")
                );

        return projects.findByOwnerId(user.getId(), effectivePageable).map(this::toResponse);
    }

    @GetMapping("/{projectId}/assets")
    public ProjectAssetsPage listAssets(@PathVariable("projectId") UUID projectId,
                                        @RequestParam(defaultValue = "1") int page,
                                        @RequestParam(defaultValue = "24") int size,
                                        @RequestParam(required = false) String search,
                                        @RequestParam(defaultValue = "false") boolean favoritesOnly) {
        var user = currentUser.requireUser();
        if (!projects.existsByIdAndOwnerId(projectId, user.getId())) {
            throw new ResponseStatusException(NOT_FOUND, "Project not found");
        }

        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), 100);
        String qPattern = (search == null || search.isBlank())
                ? null
                : "%" + search.trim().toLowerCase(Locale.ROOT) + "%";

        Page<Asset> result = assets.search(
                projectId,
                user.getId(),
                qPattern,
                favoritesOnly,
                PageRequest.of(safePage - 1, safeSize)
        );
        return new ProjectAssetsPage(
                result.getContent().stream().map(this::toAssetResponse).toList(),
                safePage,
                safeSize,
                result.getTotalElements()
        );
    }

    @DeleteMapping("/{projectId}/assets/{assetId}")
    public ResponseEntity<Void> deleteAsset(@PathVariable("projectId") UUID projectId,
                                            @PathVariable("assetId") UUID assetId) {
        var user = currentUser.requireUser();
        Asset asset = assets.findByIdAndProjectIdAndProjectOwnerId(assetId, projectId, user.getId())
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Asset not found"));

        String url = asset.getUrl();
        assets.delete(asset);
        storage.delete(url);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{projectId}/assets/{assetId}/favorite")
    public Map<String, Object> setFavorite(@PathVariable("projectId") UUID projectId,
                                           @PathVariable("assetId") UUID assetId,
                                           @RequestBody FavoriteRequest body) {
        var user = currentUser.requireUser();
        Asset asset = assets.findByIdAndProjectIdAndProjectOwnerId(assetId, projectId, user.getId())
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Asset not found"));

        asset.setFavorite(body.favorite());
        assets.save(asset);
        return Map.of("id", asset.getId(), "favorite", asset.isFavorite());
    }

    private ProjectResponse toResponse(Project project) {
        return new ProjectResponse(
                project.getId(),
                project.getName(),
                project.getCreatedAt() == null ? null : OffsetDateTime.ofInstant(project.getCreatedAt(), ZoneOffset.UTC),
                project.getUpdatedAt() == null ? null : OffsetDateTime.ofInstant(project.getUpdatedAt(), ZoneOffset.UTC)
        );
    }

    private ProjectAssetResponse toAssetResponse(Asset asset) {
        return new ProjectAssetResponse(
                asset.getId(),
                asset.getUrl(),
                asset.getFlow(),
                asset.getCreatedAt() == null ? null : OffsetDateTime.ofInstant(asset.getCreatedAt(), ZoneOffset.UTC),
                asset.isFavorite(),
                asset.getMimeType(),
                asset.getDisplayName(),
                asset.getPrompt(),
                asset.getWidth(),
                asset.getHeight()
        );
    }

    public record FavoriteRequest(boolean favorite) {}
}
