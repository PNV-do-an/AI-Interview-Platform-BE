package com.aiinterview.platform.model.dto.request;

import com.aiinterview.platform.model.enums.InterviewMode;
import com.aiinterview.platform.model.enums.QuestionType;
import com.aiinterview.platform.model.enums.SessionType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Data
public class StartInterviewRequest {

    // --- Fields for Mock / Avatar Interview ---
    @NotNull(message = "Chế độ luyện tập không được để trống")
    private InterviewMode mode;

    /** Job id — required when mode = MOCK */
    private Long jobId;

    /** Topic — required when mode = KNOWLEDGE */
    private String topic;

    @Min(value = 3, message = "Số câu hỏi tối thiểu là 3")
    @Max(value = 10, message = "Số câu hỏi tối đa là 10")
    private int questionCount = 5;

    // --- Fields for Practice Interview Program ---
    private SessionType sessionType;

    private Integer totalQuestions;

    private Integer timeLimitMinutes;

    /** Map difficulty -> percent, VD: {"BEGINNER":20,"INTERMEDIATE":50,"ADVANCED":30} */
    private Map<String, Integer> questionDistribution;

    private List<QuestionType> questionTypes = new ArrayList<>();

    private List<String> focusSkills = new ArrayList<>();
}
