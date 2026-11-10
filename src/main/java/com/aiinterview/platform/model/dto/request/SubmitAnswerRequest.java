package com.aiinterview.platform.model.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class SubmitAnswerRequest {

    /**
     * Chỉ dùng cho phiên AVATAR: lời AI service hỏi, FE relay về BE vì BE không sinh câu hỏi.
     * Phiên MOCK/KNOWLEDGE gửi field này sẽ bị 400 — đề bài của luồng đó do BE sinh ra.
     */
    @Size(max = 2000, message = "Câu hỏi tối đa 2000 ký tự")
    private String questionText;

    @Size(max = 10000, message = "Câu trả lời tối đa 10000 ký tự")
    private String answerText;

    /** Chỉ số đáp án được chọn (0-based) cho câu hỏi trắc nghiệm MOCK/KNOWLEDGE. */
    @Min(value = 0, message = "selectedOptionIndex không được âm")
    private Integer selectedOptionIndex;

    private Integer durationSec;

    /**
     * Chỉ dùng cho phiên AVATAR. Null = lượt hiện tại. Gửi tường minh để một request confirm
     * bị timeout không bị retry thành lượt kế tiếp; trỏ vào lượt đã chốt thì bị 400.
     */
    @Min(value = 0, message = "questionIndex không được âm")
    private Integer questionIndex;

    /**
     * Null hoặc true = chốt lượt (nháp STT hết sửa được, currentQuestionIndex nhích lên).
     * false = ghi đè bản nháp của lượt hiện tại, không sang lượt.
     */
    private Boolean confirm;
}
