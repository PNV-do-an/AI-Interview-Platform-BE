package com.aiinterview.platform.model.dto.response;

import com.aiinterview.platform.model.entity.InterviewAnswer;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Builder;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
public class InterviewAnswerResponse {

    private Long id;
    private int questionIndex;
    private String question;
    private String answerText;
    private List<String> options;
    private String correctAnswer;
    private Integer selectedOptionIndex;
    private Integer durationSec;
    private Integer score;
    private Boolean correct;
    private String feedback;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static InterviewAnswerResponse from(InterviewAnswer a) {
        List<String> opts = new ArrayList<>();
        if (a.getOptionsJson() != null && !a.getOptionsJson().isBlank()) {
            try {
                opts = MAPPER.readValue(a.getOptionsJson(), new TypeReference<List<String>>() {});
            } catch (Exception ignored) {
            }
        }
        Integer selected = a.getSelectedOptionIndex();
        if (selected == null && a.getAnswerText() != null && !opts.isEmpty()) {
            // suy ra chi so neu chua luu (du lieu cu)
            String at = a.getAnswerText().trim();
            for (int i = 0; i < opts.size(); i++) {
                if (opts.get(i).equalsIgnoreCase(at)) {
                    selected = i;
                    break;
                }
            }
        }
        return InterviewAnswerResponse.builder()
                .id(a.getId())
                .questionIndex(a.getQuestionIndex())
                .question(a.getQuestion())
                .answerText(a.getAnswerText())
                .options(opts)
                .correctAnswer(a.getCorrectAnswer())
                .selectedOptionIndex(selected)
                .durationSec(a.getDurationSec())
                .score(a.getScore())
                .correct(a.getScore() != null && a.getScore() >= 100)
                .feedback(a.getFeedback())
                .build();
    }
}
