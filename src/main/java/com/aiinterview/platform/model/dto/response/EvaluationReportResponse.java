package com.aiinterview.platform.model.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EvaluationReportResponse {

    private Long sessionId;
    private Double totalScore;
    private Double accuracyScore;
    private Double eloquenceScore;
    private Double technicalDepthScore;
    private Double communicationScore;

    @Builder.Default
    private List<String> strongSkills = new ArrayList<>();

    @Builder.Default
    private List<String> weakSkills = new ArrayList<>();

    private String improvementSuggestions;
    private String detailedFeedback;

    @Builder.Default
    private List<AnswerReport> answers = new ArrayList<>();

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AnswerReport {
        private Long questionId;
        private String questionText;
        private Double score;
        private String feedback;
        private Boolean isCorrect;
        private Double eloquenceScore;
        private Double coherenceScore;
    }
}