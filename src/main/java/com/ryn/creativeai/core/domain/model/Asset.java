package com.ryn.creativeai.core.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "assets")
@Getter @Setter
public class Asset {
    @Id @UuidGenerator
    private UUID id;

    @Column(name="created_at", nullable=false)
    private Instant createdAt;

    @PrePersist void prePersist(){ if (createdAt==null) createdAt = Instant.now(); }

    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "project_id", nullable = false) // o true si querés permitir null
    private Project project;

    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "job_id", nullable = false)
    private Job job;

    @Column(nullable = false)
    private String flow; // "txt2img" | "img2img" | "upscale" | "mockup"

    @Column(nullable = false, length = 255)
    private String url;

    @Column(name = "display_name", length = 255)
    private String displayName;

    @Column(name = "prompt", length = 2000)
    private String prompt;

    @Column private Integer width;   // dejalos nullable si el provider no los sabe
    @Column private Integer height;

    @Column(nullable = false)
    private boolean favorite = false;

    /** MIME del archivo guardado (image/png, image/jpeg, video/mp4, ...). */
    @Column(name = "mime_type", nullable = false, length = 64)
    private String mimeType = "image/png";
}
