package com.aiinterview.platform.model.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "user_cv")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserCv {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "file_name")
    private String fileName;

    @Column(name = "file_path")
    private String filePath;

    @Column(name = "raw_text", columnDefinition = "TEXT")
    private String rawText;

    @Column(name = "parsed_skills", columnDefinition = "json")
    private String parsedSkills;

    @Column(name = "parsed_experience", columnDefinition = "json")
    private String parsedExperience;

    @Column(name = "parsed_education", columnDefinition = "json")
    private String parsedEducation;

    @Column(name = "suggested_positions", columnDefinition = "json")
    private String suggestedPositions;

    @Column(name = "knowledge_gaps", columnDefinition = "json")
    private String knowledgeGaps;

    @Column(name = "target_position")
    private String targetPosition;

    /**
     * Vị trí đích chọn từ position_catalog trước khi upload.
     * Lưu id thường (không @ManyToOne) vì {@code open-in-view = false} và {@code getCv()} đọc
     * ngoài transaction: một quan hệ lazy sẽ nổ LazyInitializationException khi serialize.
     * Giá trị vẫn được validate với catalog lúc ghi (xem CvServiceImpl#resolveTargetPosition).
     */
    @Column(name = "target_position_id")
    private Long targetPositionId;

    @Column(name = "target_level")
    private String targetLevel;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

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