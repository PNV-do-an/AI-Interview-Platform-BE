package com.aiinterview.platform.model.entity;

import com.aiinterview.platform.common.util.SkillKeys;
import com.aiinterview.platform.model.enums.DifficultyLevel;
import com.aiinterview.platform.model.enums.QuestionArchetype;
import com.aiinterview.platform.model.enums.QuestionReviewStatus;
import com.aiinterview.platform.model.enums.QuestionType;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

// difficulty va question_type la varchar(255) san co nen index chi giu 3 cot,
// tranh vuot 3072 byte key limit cua utf8mb4
@Entity
@Table(name = "question", indexes = {
        @Index(name = "idx_question_pool_cell", columnList = "topic_id,skill_key,difficulty"),
        @Index(name = "uk_question_fingerprint", columnList = "fingerprint", unique = true),
        // Bản copy giao cho topic có content_key = NULL; MySQL cho phép nhiều NULL
        // trong unique index nên bản copy không vướng ràng buộc này.
        @Index(name = "uk_question_skill_content", columnList = "skill_key,content_key", unique = true)
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Question {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "topic_id")
    private StudyTopic topic;

    // Bộ chứa câu hỏi. NULL ở bản copy đã giao cho topic (spec §4.2).
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "set_id")
    private QuestionSet set;

    @Column(name = "skill_tag", nullable = false)
    private String skillTag;

    @Enumerated(EnumType.STRING)
    @Column(name = "difficulty", nullable = false)
    private DifficultyLevel difficulty;

    @Enumerated(EnumType.STRING)
    @Column(name = "question_type", nullable = false)
    private QuestionType questionType;

    @Column(name = "question_text", nullable = false, columnDefinition = "TEXT")
    private String questionText;

    @Column(name = "options", columnDefinition = "json")
    private String options;

    @Column(name = "correct_answer", columnDefinition = "TEXT")
    private String correctAnswer;

    @Column(name = "explanation", columnDefinition = "TEXT")
    private String explanation;

    @Column(name = "ai_generated")
    @Builder.Default
    private Boolean aiGenerated = true;

    @Column(name = "source")
    @Builder.Default
    private String source = "AI";

    @Column(name = "cache_key")
    private String cacheKey;

    @Column(name = "skill_key", length = 100)
    private String skillKey;

    @Column(name = "language", length = 8)
    private String language;

    @Column(name = "fingerprint", length = 64)
    private String fingerprint;

    // Dạng câu trong bộ: DEFINITION / COMPARISON / APPLICATION / DEBUGGING / TRADE_OFF / SCENARIO.
    // Row cũ (set_id = NULL) để trống; không backfill (spec §4.5).
    @Enumerated(EnumType.STRING)
    @Column(name = "archetype", length = 20)
    private QuestionArchetype archetype;

    // Hash sau chuẩn hoá câu hỏi (bỏ stopword + synonym + sort). NULL ở bản copy.
    @Column(name = "content_key", length = 64)
    private String contentKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "review_status", length = 16, nullable = false)
    @Builder.Default
    private QuestionReviewStatus reviewStatus = QuestionReviewStatus.AUTO_APPROVED;

    @Column(name = "pool_question_id")
    private Long poolQuestionId;

    @Column(name = "times_used")
    @Builder.Default
    private Integer timesUsed = 0;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (timesUsed == null) {
            timesUsed = 0;
        }
        if (skillKey == null && skillTag != null) {
            skillKey = SkillKeys.normalize(skillTag);
        }
        if (language == null) {
            language = "en";
        }
        if (reviewStatus == null) {
            reviewStatus = QuestionReviewStatus.AUTO_APPROVED;
        }
    }
}