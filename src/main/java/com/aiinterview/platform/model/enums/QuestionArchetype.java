package com.aiinterview.platform.model.enums;

import java.util.Locale;

/**
 * Dạng câu hỏi trong một bộ — độ đa dạng của bộ, không phải độ khó.
 * Mỗi bộ phải cân theo quota (spec 2026-11-24 §6.2) để không ra toàn câu định nghĩa.
 */
public enum QuestionArchetype {

    /** Định nghĩa / giải thích khái niệm. */
    DEFINITION,

    /** So sánh hai hay nhiều khái niệm. */
    COMPARISON,

    /** Áp dụng vào một tình huống cụ thể. */
    APPLICATION,

    /** Tìm lỗi trong đoạn code / cấu hình. */
    DEBUGGING,

    /** Đánh đổi giữa hai lựa chọn. */
    TRADE_OFF,

    /** Xử lý tình huống mở (hệ thống, sự cố, quy trình). */
    SCENARIO;

    /**
     * AI có thể trả về nhãn lạ hoặc sai định dạng; khi đó hạ về DEFINITION
     * thay vì làm hỏng cả bộ (spec §6).
     */
    public static QuestionArchetype from(String raw) {
        if (raw == null || raw.isBlank()) {
            return DEFINITION;
        }
        String value = raw.trim()
                .toUpperCase(Locale.ROOT)
                .replace('-', '_')
                .replace(' ', '_');
        for (QuestionArchetype candidate : values()) {
            if (candidate.name().equals(value)) {
                return candidate;
            }
        }
        return DEFINITION;
    }
}
