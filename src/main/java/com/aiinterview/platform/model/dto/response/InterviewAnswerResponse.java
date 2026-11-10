package com.aiinterview.platform.model.dto.response;

import com.aiinterview.platform.model.entity.InterviewAnswer;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class InterviewAnswerResponse {

    private Long id;
    private int questionIndex;
    private String question;
    private String answerText;
    private Integer durationSec;
    private Integer score;
    private Boolean correct;
    private String feedback;

    public static InterviewAnswerResponse from(InterviewAnswer a) {
        return InterviewAnswerResponse.builder()
                .id(a.getId())
                .questionIndex(a.getQuestionIndex())
                .question(a.getQuestion())
                .answerText(a.getAnswerText())
                .durationSec(a.getDurationSec())
                .score(a.getScore())
                .correct(a.getScore() != null && a.getScore() >= 100)
                .feedback(a.getFeedback())
                .build();
    }
}
