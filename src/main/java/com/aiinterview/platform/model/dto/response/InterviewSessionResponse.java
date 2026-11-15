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
    private int questionCount;
    private List<QuizQuestionResponse> questions;
    private int currentQuestionIndex;
    private int answeredCount;
    private Integer overallScore;
    private LocalDateTime createdAt;
    private LocalDateTime completedAt;

    public static InterviewSessionResponse from(InterviewSession s, int answeredCount) {
        // Phiên cũ trước khi có cột questionCount mang giá trị mặc định 5; với MOCK/KNOWLEDGE
        // số câu thật luôn bằng danh sách câu hỏi đã sinh, nên lấy theo danh sách.
        int questionCount = s.getQuestionCount();
        if (s.getMode() != InterviewMode.AVATAR && !s.getQuestions().isEmpty()) {
            questionCount = s.getQuestions().size();
        }
        if (questionCount <= 0) {
            questionCount = 5;
        }
        return InterviewSessionResponse.builder()
                .id(s.getId())
                .mode(s.getMode())
                .topic(s.getTopic())
                .jobId(s.getJob() != null ? s.getJob().getId() : null)
                .jobTitle(s.getJob() != null ? s.getJob().getTitle() : null)
                .companyName(s.getJob() != null ? s.getJob().getCompanyName() : null)
                .status(s.getStatus())
                .questionCount(questionCount)
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
