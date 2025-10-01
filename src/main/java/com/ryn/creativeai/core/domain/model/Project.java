package com.ryn.creativeai.core.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "projects")
@Getter @Setter
public class Project {
    @Id @UuidGenerator private UUID id;

    @Column(nullable = false) private String name;

    @Column(name="created_at", nullable=false) private Instant createdAt;
    @Column(name="updated_at", nullable=false) private Instant updatedAt;

    @PrePersist void prePersist(){ var now=Instant.now(); createdAt=now; updatedAt=now; }
    @PreUpdate  void preUpdate(){ updatedAt=Instant.now(); }
}
