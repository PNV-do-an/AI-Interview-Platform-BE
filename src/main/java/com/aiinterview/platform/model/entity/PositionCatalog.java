package com.aiinterview.platform.model.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Vi tri (role) trong catalog dung cho recommendation theo CV.
 * Bo ky nang yeu cau nam o {@link PositionRequiredSkill}.
 */
@Entity
@Table(name = "position_catalog")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PositionCatalog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "slug", nullable = false, unique = true)
    private String slug;

    @Column(name = "level")
    private String level;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    /** JD goc do admin nhap; dung de AI trich xuat ky nang yeu cau va de admin sua lai sau nay. */
    @Column(name = "jd_text", columnDefinition = "TEXT")
    private String jdText;

    @Column(name = "active", nullable = false)
    @Builder.Default
    private Boolean active = true;

    // Loai khoi equals/toString de tranh de quy qua PositionRequiredSkill.position
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    @OneToMany(mappedBy = "position", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<PositionRequiredSkill> requiredSkills = new ArrayList<>();

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public void addRequiredSkill(String skillName, String skillKey) {
        requiredSkills.add(PositionRequiredSkill.builder()
                .position(this)
                .skillName(skillName)
                .skillKey(skillKey)
                .build());
    }

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
