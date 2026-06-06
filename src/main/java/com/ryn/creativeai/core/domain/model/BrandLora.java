package com.ryn.creativeai.core.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "brand_loras")
@Getter @Setter
public class BrandLora {

    @Id @UuidGenerator
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    @Column(nullable = false, length = 80)
    private String name;

    @Column(name = "trigger_word", nullable = false, length = 40)
    private String triggerWord;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private BrandLoraStatus status = BrandLoraStatus.PENDING;

    @Column(nullable = false)
    private Integer progress = 0;

    @Lob
    @Column(name = "error_message")
    private String errorMessage;

    /** Path absoluto del safetensors entrenado (null hasta COMPLETED). */
    @Column(name = "safetensors_path", length = 1024)
    private String safetensorsPath;

    @Column(name = "strength_model", nullable = false)
    private Double strengthModel = 0.8;

    @Column(name = "strength_clip", nullable = false)
    private Double strengthClip = 0.8;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = Instant.now();
    }
}
