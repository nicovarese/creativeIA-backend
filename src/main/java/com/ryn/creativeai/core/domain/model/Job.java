package com.ryn.creativeai.core.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "jobs")
@Getter @Setter
public class Job {

    @Id
    @UuidGenerator
    private UUID id;

    @Column(name = "template_key", nullable = false)
    private String templateKey;

    @Column(name = "template_ver", nullable = false)
    private String templateVer;

    @Column(nullable = false)
    private String provider;   // "mock" | "comfyui"

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private JobStatus status;

    @Lob
    @Column(name = "compiled_json", nullable = false)
    private String compiledJson;

    @Lob
    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /* ---------- Métodos de dominio (los que te faltan) ---------- */

    public void markQueued()  { this.status = JobStatus.QUEUED; touch(); }
    public void markRunning() { this.status = JobStatus.RUNNING; touch(); }
    public void markDone()    { this.status = JobStatus.DONE;    this.errorMessage = null; touch(); }

    public void markFailed(String message) {
        this.status = JobStatus.FAILED;
        this.errorMessage = message;
        touch();
    }

    private void touch() { this.updatedAt = Instant.now(); }

    @PrePersist
    public void prePersist() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
        if (this.status == null) this.status = JobStatus.QUEUED;
    }
}
