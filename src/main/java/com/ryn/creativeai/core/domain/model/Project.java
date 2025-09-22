package com.ryn.creativeai.core.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

@Entity @Table(name = "projects")
@Getter @Setter
public class Project {

    @Id @UuidGenerator
    private UUID id;

    @Column(nullable = false, unique = true)
    private String name;

    // Opcional: para multi-tenant/autorización
    @Column(name = "owner_user_id")
    private String ownerUserId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void preUpdate() { this.updatedAt = Instant.now(); }
}
