package com.aiinterview.platform.model.enums;

public enum InterviewMode {
    MOCK,
    KNOWLEDGE,
    /**
     * Phỏng vấn với avatar AI (spec 012). Câu hỏi do AI service sinh trong lúc hội thoại,
     * FE relay transcript về đây bằng JWT của chính ứng viên; BE không sinh câu hỏi trước.
     */
    AVATAR
}
