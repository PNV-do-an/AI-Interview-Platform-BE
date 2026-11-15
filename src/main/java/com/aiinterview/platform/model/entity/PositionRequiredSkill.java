package com.aiinterview.platform.model.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "position_required_skill",
        uniqueConstraints = @UniqueConstraint(name = "uk_position_skill", columnNames = {"position_id", "skill_key"}))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PositionRequiredSkill {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "position_id", nullable = false)
    private PositionCatalog position;

    @Column(name = "skill_name", nullable = false)
    private String skillName;

    /** Dang chuan hoa cua skill_name (SkillKeys.normalize) de so khop voi ky nang trong CV. */
    @Column(name = "skill_key", nullable = false)
    private String skillKey;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
