package com.aiinterview.platform.model.entity;

import com.aiinterview.platform.model.enums.SubtopicSource;
import com.aiinterview.platform.model.enums.SubtopicStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Mảng kiến thức của một skill — nguồn duy nhất của khái niệm "subtopic" (spec §4.8).
 * Identity là CẶP (skill_key, subtopic_key), không phải subtopic_key đơn lẻ:
 * "networking" của skill "docker" và của skill "computer networking" là hai row khác nhau.
 */
@Entity
@Table(name = "skill_subtopic",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_skill_subtopic",
                columnNames = {"skill_key", "subtopic_key"}),
        indexes = @Index(name = "idx_skill_subtopic_list",
                columnList = "skill_key,status,sort_order"))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SkillSubtopic {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "skill_key", length = 100, nullable = false)
    private String skillKey;

    @Column(name = "subtopic_key", length = 100, nullable = false)
    private String subtopicKey;

    // Tên hiển thị cho admin đọc; viết đủ nghĩa (ví dụ "Docker networking").
    @Column(name = "subtopic_name", length = 120, nullable = false)
    private String subtopicName;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", length = 16, nullable = false)
    private SubtopicSource source;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 16, nullable = false)
    @Builder.Default
    private SubtopicStatus status = SubtopicStatus.ACTIVE;

    @Column(name = "sort_order", nullable = false)
    @Builder.Default
    private Integer sortOrder = 0;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (status == null) {
            status = SubtopicStatus.ACTIVE;
        }
        if (sortOrder == null) {
            sortOrder = 0;
        }
    }
}
