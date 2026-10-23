package com.aiinterview.platform.model.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/**
 * Lịch sử chuyển trạng thái của một đơn ứng tuyển (FR-APP-05).
 * Mỗi lần đổi status từ PENDING → SHORTLISTED → ... đều ghi 1 dòng.
 */
@Entity
@Table(name = "application_status_history", indexes = {
        @Index(columnList = "application_id, acted_at")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApplicationStatusHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "application_id", nullable = false)
    private Application application;

    /** From/to là các giá trị ENUM: PENDING, SHORTLISTED, INTERVIEWED... fromStatus null ở dòng đầu (nộp đơn). */
    @Column
    private String fromStatus;

    @Column(nullable = false)
    private String toStatus;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_user_id")
    private User actor;  // employer đánh giá hoặc hệ thống đặt lịch

    /** Ghi chú nội bộ chỉ employer/admin thấy, không hiển thị cho candidate (BR-09). */
    @Column(columnDefinition = "TEXT")
    private String note;

    @Column(updatable = false)
    private LocalDateTime actedAt;

    @PrePersist
    protected void onCreate() {
        actedAt = LocalDateTime.now();
    }
}
