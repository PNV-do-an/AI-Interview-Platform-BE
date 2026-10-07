package com.aiinterview.platform.model.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "interview_evaluation")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InterviewEvaluation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false, unique = true)
    private InterviewSession session;

    @Column(name = "total_score")
    private Double totalScore;

    @Column(name = "accuracy_score")
    private Double accuracyScore;

    @Column(name = "eloquence_score")
    private Double eloquenceScore;

    @Column(name = "technical_depth_score")
    private Double technicalDepthScore;

    @Column(name = "communication_score")
    private Double communicationScore;

    @Column(name = "weak_skills", columnDefinition = "json")
    private String weakSkills;

    @Column(name = "strong_skills", columnDefinition = "json")
    private String strongSkills;

    @Column(name = "improvement_suggestions", columnDefinition = "TEXT")
    private String improvementSuggestions;

    @Column(name = "detailed_feedback", columnDefinition = "TEXT")
    private String detailedFeedback;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}