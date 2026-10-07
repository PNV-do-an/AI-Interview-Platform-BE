package com.aiinterview.platform.model.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "interview_answer")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InterviewAnswer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false)
    private InterviewSession session;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "question_id", nullable = false)
    private Question question;

    @Column(name = "answer_order", nullable = false)
    private Integer answerOrder;

    @Column(name = "user_text_answer", columnDefinition = "TEXT")
    private String userTextAnswer;

    @Column(name = "audio_file_path", length = 500)
    private String audioFilePath;

    @Column(name = "audio_duration_seconds")
    private Integer audioDurationSeconds;

    @Column(name = "audio_transcription", columnDefinition = "TEXT")
    private String audioTranscription;

    @Column(name = "is_correct")
    private Boolean isCorrect;

    @Column(name = "score")
    private Double score;

    @Column(name = "ai_feedback", columnDefinition = "TEXT")
    private String aiFeedback;

    @Column(name = "eloquence_score")
    private Double eloquenceScore;

    @Column(name = "coherence_score")
    private Double coherenceScore;

    @Column(name = "answer_submitted_at", updatable = false)
    private LocalDateTime answerSubmittedAt;

    @PrePersist
    protected void onCreate() {
        answerSubmittedAt = LocalDateTime.now();
        if (answerOrder == null) {
            answerOrder = 0;
        }
    }
}