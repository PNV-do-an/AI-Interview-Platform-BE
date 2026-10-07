package com.aiinterview.platform.model.dto.response;

import com.aiinterview.platform.model.enums.SessionStatus;
import com.aiinterview.platform.model.enums.SessionType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InterviewSessionResponse {

    private Long id;
    private SessionType sessionType;
    private String targetPosition;
    private Integer totalQuestions;
    private Integer timeLimitMinutes;
    private SessionStatus status;
    private Integer currentQuestionIndex;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private Double overallScore;
    private String overallFeedback;

    @Builder.Default
    private Map<String, Integer> questionDistribution = new LinkedHashMap<>();
}