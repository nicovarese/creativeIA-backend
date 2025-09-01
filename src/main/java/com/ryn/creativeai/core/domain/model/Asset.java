package com.ryn.creativeai.core.domain.model;

import jakarta.persistence.*;
import lombok.Getter; import lombok.NoArgsConstructor; import lombok.Setter;
import java.util.UUID;

@Entity @Table(name="asset")
@Getter @Setter @NoArgsConstructor
public class Asset {
    @Id private UUID id = UUID.randomUUID();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "job_id", nullable = false)
    private Job job;

    @Column(nullable=false) private String url;
    private Integer width; private Integer height;
}
