package com.aiinterview.platform.model.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Báo cáo của một phiên AVATAR, quan hệ 1-1 với session (spec 012).
 * Điểm tổng do BE trung bình 5 chiều rồi làm tròn; value FE/AI gửi cho "overallScore" bị bỏ qua.
 */
@Entity
@Table(name = "interview_report")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InterviewReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false, unique = true)
    private InterviewSession session;

    @Column(nullable = false)
    private int chuyenMon;

    @Column(nullable = false)
    private int duAn;

    @Column(nullable = false)
    private int tuDuy;

    @Column(nullable = false)
    private int truyenDat;

    @Column(nullable = false)
    private int mucKhop;

    @Column(nullable = false)
    private int overallScore;

    @Column(columnDefinition = "TEXT")
    private String strengths;

    @Column(columnDefinition = "TEXT")
    private String improvements;

    @Column(nullable = false)
    private String rubricVersion;

    @Column(updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
