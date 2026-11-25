package com.aiinterview.platform.model.entity;

import com.aiinterview.platform.model.enums.DifficultyLevel;
import com.aiinterview.platform.model.enums.QuestionSetScope;
import com.aiinterview.platform.model.enums.QuestionType;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Một bộ câu hỏi 10–20 câu: đơn vị sinh và đơn vị phục vụ (spec D1, D9).
 * Bộ KHÔNG gắn user và KHÔNG gắn position — bộ là tài sản dùng chung (D14).
 * Quan hệ "user này đã nhận bộ nào, lúc nào" nằm ở {@link UserSetUsage}.
 */
@Entity
@Table(name = "question_set",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_question_set_name",
                columnNames = "set_name"),
        indexes = @Index(name = "idx_question_set_cell",
                columnList = "skill_key,difficulty,question_type,language,times_used"))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuestionSet {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "set_name", length = 120, nullable = false)
    private String setName;

    @Enumerated(EnumType.STRING)
    @Column(name = "scope", length = 16, nullable = false)
    private QuestionSetScope scope;

    @Column(name = "skill_key", length = 100, nullable = false)
    private String skillKey;

    @Column(name = "skill_name")
    private String skillName;

    @Enumerated(EnumType.STRING)
    @Column(name = "difficulty", length = 20, nullable = false)
    private DifficultyLevel difficulty;

    @Enumerated(EnumType.STRING)
    @Column(name = "question_type", length = 20, nullable = false)
    private QuestionType questionType;

    @Column(name = "language", length = 8, nullable = false)
    private String language;

    // Bối cảnh chung của bộ: mọi câu phải nằm trong bối cảnh này (spec §6).
    @Column(name = "theme", nullable = false)
    private String theme;

    // Mỗi bộ thuộc đúng 1 subtopic, kể cả bộ do AI tự đặt nhãn (D17).
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "subtopic_id", nullable = false)
    private SkillSubtopic subtopic;

    // Snapshot quota dùng lúc sinh, để đọc lại biết bộ này cân theo tỉ lệ nào.
    @Column(name = "archetype_quota", columnDefinition = "json", nullable = false)
    private String archetypeQuota;

    @Column(name = "times_used", nullable = false)
    @Builder.Default
    private Integer timesUsed = 0;

    // NULL = hệ thống tự sinh; có giá trị = admin tạo tay (D13).
    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (timesUsed == null) {
            timesUsed = 0;
        }
    }
}
