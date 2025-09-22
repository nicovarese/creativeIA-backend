package com.ryn.creativeai.infra;

import com.ryn.creativeai.core.domain.model.Project;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ProjectRepository extends JpaRepository<Project, UUID> {
    Optional<Project> findByName(String name);
}
