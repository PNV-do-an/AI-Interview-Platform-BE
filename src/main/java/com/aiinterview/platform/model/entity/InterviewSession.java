package com.aiinterview.platform.model.entity;

import com.aiinterview.platform.model.enums.InterviewMode;
import com.aiinterview.platform.model.enums.InterviewSessionStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "interview_sessions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InterviewSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "candidate_id", nullable = false)
    private User candidate;

    /** Job chosen for MOCK mode; null for KNOWLEDGE practice */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "job_id")
    private JobDescription job;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private InterviewMode mode;

    /** Topic for KNOWLEDGE mode (e.g. "Java Spring Boot") */
    private String topic;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private InterviewSessionStatus status = InterviewSessionStatus.IN_PROGRESS;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "interview_session_questions", joinColumns = @JoinColumn(name = "session_id"))
    @Builder.Default
    @OrderColumn(name = "idx")
    private List<QuizQuestion> questions = new ArrayList<>();

    @Column(nullable = false)
    @Builder.Default
    private int currentQuestionIndex = 0;

    private Integer overallScore;

    @Column(updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime completedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
