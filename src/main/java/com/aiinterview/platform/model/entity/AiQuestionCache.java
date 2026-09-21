package com.aiinterview.platform.model.entity;

import com.aiinterview.platform.model.enums.DifficultyLevel;
import com.aiinterview.platform.model.enums.QuestionType;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "ai_question_cache")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiQuestionCache {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "cache_key", nullable = false, unique = true)
    private String cacheKey;

    @Column(name = "position")
    private String position;

    @Column(name = "skill_tag")
    private String skillTag;

    @Enumerated(EnumType.STRING)
    @Column(name = "difficulty")
    private DifficultyLevel difficulty;

    @Enumerated(EnumType.STRING)
    @Column(name = "question_type")
    private QuestionType questionType;

    @Column(name = "question_text", nullable = false, columnDefinition = "TEXT")
    private String questionText;

    @Column(name = "options", columnDefinition = "json")
    private String options;

    @Column(name = "correct_answer", columnDefinition = "TEXT")
    private String correctAnswer;

    @Column(name = "explanation", columnDefinition = "TEXT")
    private String explanation;

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
    }
}