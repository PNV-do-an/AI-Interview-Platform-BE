package com.aiinterview.platform.model.enums;

/**
 * Hai dạng câu hỏi duy nhất của hệ thống.
 */
public enum QuestionType {

    /** Trắc nghiệm: {@code options} chứa 4 lựa chọn, {@code correct_answer} là key của đáp án đúng ("A"). */
    MCQ,

    /** Tự luận: trả lời bằng văn bản, {@code options} null, chấm điểm bằng AI. */
    ESSAY;

    /** Thay cho {@code type == QuestionType.MCQ} ở tầng dựng prompt và serialize options. */
    public boolean isMultipleChoice() {
        return this == MCQ;
    }
}
