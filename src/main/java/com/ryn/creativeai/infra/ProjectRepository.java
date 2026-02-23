package com.ryn.creativeai.infra;

import com.ryn.creativeai.core.domain.model.Project;
import org.springframework.data.jpa.repository.JpaRepository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;
import java.util.UUID;

public interface ProjectRepository extends JpaRepository<Project, UUID> {
    Optional<Project> findByName(String name);
    Page<Project> findByOwnerId(UUID ownerId, Pageable pageable);
    Optional<Project> findByIdAndOwnerId(UUID id, UUID ownerId);
    boolean existsByIdAndOwnerId(UUID id, UUID ownerId);
}
