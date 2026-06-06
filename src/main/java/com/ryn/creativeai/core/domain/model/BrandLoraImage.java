package com.ryn.creativeai.core.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "brand_lora_images")
@Getter @Setter
public class BrandLoraImage {

    @Id @UuidGenerator
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "brand_lora_id", nullable = false)
    private BrandLora brandLora;

    @Column(nullable = false, length = 255)
    private String filename;

    @Column(nullable = false, length = 1024)
    private String path;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = Instant.now();
    }
}
