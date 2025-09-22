package com.ryn.creativeai.core.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

@Entity @Table(name = "assets", indexes = {
        @Index(name = "idx_assets_project", columnList = "project_id"),
        @Index(name = "idx_assets_job",     columnList = "job_id")
})
@Getter @Setter
public class Asset {

    @Id @UuidGenerator
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "job_id")
    private Job job;

    @Column(nullable = false)
    private String url;

    @Column private Integer width;
    @Column private Integer height;

    @Column(nullable = false)
    private String flow;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    void prePersist() { this.createdAt = Instant.now(); }
}
