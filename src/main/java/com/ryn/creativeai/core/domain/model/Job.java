package com.ryn.creativeai.core.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

@Entity @Table(name = "jobs")
@Getter @Setter
public class Job {

    @Id @UuidGenerator
    private UUID id;

    /* --- Relaciones --- */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id")   // <-- coincide con la columna nueva
    private Project project;

    /* --- Template/Provider --- */
    @Column(name = "template_key", nullable = false)
    private String templateKey;

    @Column(name = "template_ver", nullable = false)
    private String templateVer;

    @Column(nullable = false)
    private String provider;   // "mock" | "comfyui"

    /* --- Estado --- */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private JobStatus status;

    /* --- Payload compilado --- */
    @Lob
    @Column(name = "compiled_json", nullable = false)
    private String compiledJson;

    /* --- Error --- */
    @Lob @Column(name = "error_message")
    private String errorMessage;

    /* --- Timestamps --- */
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /* --- Metadatos opcionales por flow --- */
    @Column(name = "flow_raw") private String flow; // txt2img | img2img | upscale | mockup
    @Column(length = 4000) private String prompt;
    @Column private Integer width;
    @Column private Integer height;
    @Column private Integer batch;

    @Column private String style;
    @Column private String brand;
    @Column private String product;

    @Column private Long seed;
    @Column private Double strength; // img2img
    @Column private Integer resolution;  // upscale
    @Column private String template; // mockup
    @Column(name = "scale_pct") private Integer scale;
    @Column(name = "offset_x")  private Integer offsetX;
    @Column(name = "offset_y")  private Integer offsetY;

    /* --- Dominio --- */
    @Column(nullable = false)
    private Integer progress = 0;

    public void markQueued()  { this.status = JobStatus.QUEUED; this.progress = 0;  touch(); }
    public void markRunning() { this.status = JobStatus.RUNNING; this.progress = 5;  touch(); }
    public void markDone()    { this.status = JobStatus.DONE;    this.progress = 100;this.errorMessage=null; touch(); }
    public void markFailed(String message) { this.status = JobStatus.FAILED; this.errorMessage = message; touch(); }

    public void setProgressSafe(int p){ this.progress = Math.max(0, Math.min(100, p)); touch(); }

    private void touch() { this.updatedAt = Instant.now(); }

    @PrePersist
    public void prePersist() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
        if (this.status == null) this.status = JobStatus.QUEUED;
    }
}
