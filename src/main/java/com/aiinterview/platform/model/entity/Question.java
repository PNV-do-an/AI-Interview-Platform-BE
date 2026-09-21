package com.aiinterview.platform.model.entity;

import com.aiinterview.platform.model.enums.DifficultyLevel;
import com.aiinterview.platform.model.enums.QuestionType;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "question")
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