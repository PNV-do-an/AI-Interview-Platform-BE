package com.aiinterview.platform.model.dto.request;

import com.aiinterview.platform.model.enums.InterviewMode;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class StartInterviewRequest {

    @NotNull(message = "Chế độ luyện tập không được để trống")
    private InterviewMode mode;

    /** Job id — required when mode = MOCK */
    private Long jobId;

    /** Topic — required when mode = KNOWLEDGE */
    private String topic;

    @Min(value = 3, message = "Số câu hỏi tối thiểu là 3")
    @Max(value = 10, message = "Số câu hỏi tối đa là 10")
    private int questionCount = 5;
}
