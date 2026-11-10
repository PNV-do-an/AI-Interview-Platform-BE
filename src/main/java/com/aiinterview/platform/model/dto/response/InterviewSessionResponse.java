package com.aiinterview.platform.model.dto.response;

import com.aiinterview.platform.model.dto.response.QuizQuestionResponse;
import com.aiinterview.platform.model.entity.InterviewSession;
import com.aiinterview.platform.model.enums.InterviewMode;
import com.aiinterview.platform.model.enums.InterviewSessionStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class InterviewSessionResponse {

    private Long id;
    private InterviewMode mode;
    private String topic;
    private Long jobId;
    private String jobTitle;
    private String companyName;
    private InterviewSessionStatus status;
    private List<QuizQuestionResponse> questions;
    private int currentQuestionIndex;
    private int answeredCount;
    private Integer overallScore;
    private LocalDateTime createdAt;
    private LocalDateTime completedAt;

    public static InterviewSessionResponse from(InterviewSession s, int answeredCount) {
        return InterviewSessionResponse.builder()
                .id(s.getId())
                .mode(s.getMode())
                .topic(s.getTopic())
                .jobId(s.getJob() != null ? s.getJob().getId() : null)
                .jobTitle(s.getJob() != null ? s.getJob().getTitle() : null)
                .companyName(s.getJob() != null ? s.getJob().getCompanyName() : null)
                .status(s.getStatus())
                .questions(s.getQuestions().stream()
                        .map(q -> QuizQuestionResponse.builder()
                                .question(q.getQuestion())
                                .text(q.getQuestion())
                                .options(q.getOptions())
                                .choices(q.getOptions())
                                .build())
                        .toList())
                .currentQuestionIndex(s.getCurrentQuestionIndex())
                .answeredCount(answeredCount)
                .overallScore(s.getOverallScore())
                .createdAt(s.getCreatedAt())
                .completedAt(s.getCompletedAt())
                .build();
    }
}
