package com.aiinterview.platform.model.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "ai_credit_packages")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiCreditPackage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(length = 1000)
    private String description;

    @Column(nullable = false)
    private Long price;

    @Column(nullable = false)
    private Integer credits;

    /** Discount percent capped at 5. */
    @Column(nullable = false)
    @Builder.Default
    private Integer discountPercent = 0;

    @Column(nullable = false)
    @Builder.Default
    private Integer validityDays = 365;

    @Column(nullable = false)
    @Builder.Default
    private Boolean active = true;

    @Column(updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (active == null) active = true;
        if (discountPercent == null) discountPercent = 0;
        if (discountPercent > 5) discountPercent = 5;
        if (validityDays == null) validityDays = 365;
    }
}
