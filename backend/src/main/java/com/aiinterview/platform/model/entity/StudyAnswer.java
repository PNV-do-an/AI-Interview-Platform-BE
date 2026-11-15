package com.aiinterview.platform.model.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "study_answer",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_study_answer_topic_question",
                columnNames = {"topic_id", "question_id"}),
        indexes = @Index(name = "idx_study_answer_user_pool",
                columnList = "user_id,pool_question_id"))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StudyAnswer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "topic_id", nullable = false)
    private StudyTopic topic;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "question_id", nullable = false)
    private Question question;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // Cau hoi goc trong pool, dung de loai cau da lam qua nhieu chuong trinh
    @Column(name = "pool_question_id")
    private Long poolQuestionId;

    @Column(name = "answer", columnDefinition = "TEXT")
    private String answer;

    @Column(name = "score")
    private Double score;

    @Column(name = "is_correct")
    private Boolean isCorrect;

    @Column(name = "attempt_count")
    @Builder.Default
    private Integer attemptCount = 1;

    @Column(name = "submitted_at", nullable = false)
    private LocalDateTime submittedAt;

    @PrePersist
    protected void onCreate() {
        if (submittedAt == null) {
            submittedAt = LocalDateTime.now();
        }
        if (attemptCount == null) {
            attemptCount = 1;
        }
    }
}
