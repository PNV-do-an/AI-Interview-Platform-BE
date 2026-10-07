package com.aiinterview.platform.model.dto.request;

import com.aiinterview.platform.model.enums.QuestionType;
import com.aiinterview.platform.model.enums.SessionType;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Data
public class StartInterviewRequest {

    private SessionType sessionType;

    private Integer totalQuestions;

    private Integer timeLimitMinutes;

    /** Map difficulty -> percent, VD: {"BEGINNER":20,"INTERMEDIATE":50,"ADVANCED":30} */
    private Map<String, Integer> questionDistribution;

    private List<QuestionType> questionTypes = new ArrayList<>();

    private List<String> focusSkills = new ArrayList<>();
}