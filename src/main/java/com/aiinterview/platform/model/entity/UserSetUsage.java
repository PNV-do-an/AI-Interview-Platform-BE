package com.aiinterview.platform.model.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * User đã nhận bộ nào, lúc nào — nguồn dữ liệu của rotation (spec §4.7, §7.3).
 * Tách khỏi question_set vì bộ là tài sản dùng chung, không có cột user_id.
 */
@Entity
@Table(name = "user_set_usage",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_user_set",
                columnNames = {"user_id", "set_id"}),
        indexes = @Index(name = "idx_user_set_available",
                columnList = "user_id,available_at"))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserSetUsage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "set_id", nullable = false)
    private QuestionSet set;

    @Column(name = "use_count", nullable = false)
    @Builder.Default
    private Integer useCount = 0;

    @Column(name = "last_used_at", nullable = false)
    private LocalDateTime lastUsedAt;

    // Mốc được cấp lại. NULL = đang làm dở, không cấp lại (spec §4.7).
    // Được tính MỘT LẦN khi topic hoàn thành, theo điểm trung bình của bộ.
    @Column(name = "available_at")
    private LocalDateTime availableAt;
}
