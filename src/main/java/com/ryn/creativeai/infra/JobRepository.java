package com.ryn.creativeai.infra;

import com.ryn.creativeai.core.domain.model.Job;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface JobRepository extends JpaRepository<Job, UUID> {
    Page<Job> findByProjectId(UUID projectId, Pageable pageable);
}