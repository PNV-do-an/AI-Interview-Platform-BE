package com.aiinterview.platform.model.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "mentor_pricing")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MentorPricing {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "mentor_id", nullable = false, unique = true)
    private User mentor;

    @Column(nullable = false)
    @Builder.Default
    private Long minPrice = 0L;

    @Column(nullable = false)
    @Builder.Default
    private Long maxPrice = Long.MAX_VALUE;

    @Column(nullable = false)
    @Builder.Default
    private Long hourlyRate = 0L;

    @Column(length = 30)
    @Builder.Default
    private String level = "STANDARD";

    @Column(nullable = false)
    @Builder.Default
    private Boolean featured = false;

    private LocalDateTime updatedAt;

    @PrePersist
    @PreUpdate
    protected void onSave() {
        updatedAt = LocalDateTime.now();
    }
}
