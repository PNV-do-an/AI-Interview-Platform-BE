package com.aiinterview.platform.model.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "interview_answers")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InterviewAnswer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    private InterviewSession session;

    @Column(nullable = false)
    private int questionIndex;

    @Column(nullable = false, length = 2000)
    private String question;

    @Column(length = 10000)
    private String answerText;

    /** Seconds the candidate spent on this question */
    private Integer durationSec;

    /** AI score 0-100 */
    private Integer score;

    /** AI feedback in Vietnamese */
    @Column(length = 2000)
    private String feedback;

    private LocalDateTime answeredAt;

    /**
     * Chỉ dùng cho phiên AVATAR: null = bản nháp transcript do STT trả về và còn sửa được,
     * có giá trị = ứng viên đã chốt lượt này, mọi lần ghi lại vào cùng questionIndex bị từ chối.
     */
    private LocalDateTime confirmedAt;

    @PrePersist
    protected void onCreate() {
        if (answeredAt == null) {
            answeredAt = LocalDateTime.now();
        }
    }
}
