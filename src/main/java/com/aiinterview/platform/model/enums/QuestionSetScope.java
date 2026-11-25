package com.aiinterview.platform.model.enums;

/**
 * Phạm vi sử dụng của một bộ câu hỏi.
 * Bộ câu hỏi là tài sản dùng chung (không gắn user, không gắn position) — scope
 * chỉ quyết định prompt sinh câu và cách ghép bộ ở phase 2.
 */
public enum QuestionSetScope {

    /** Học tập: bộ thuộc một skill, phục vụ cho study_topic. */
    STUDY,

    /** Phỏng vấn: bộ được ghép nhiều skill theo position_required_skill (phase 2). */
    INTERVIEW
}
