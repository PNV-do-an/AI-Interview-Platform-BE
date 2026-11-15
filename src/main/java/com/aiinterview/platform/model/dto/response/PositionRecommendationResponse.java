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
public class PositionRecommendationResponse {

    private Long positionId;
    private String title;
    private String slug;
    private String level;
    private String description;

    private int requiredSkillCount;
    private int matchedSkillCount;
    private int matchPercentage;

    @Builder.Default
    private List<String> matchedSkills = new ArrayList<>();

    @Builder.Default
    private List<String> missingSkills = new ArrayList<>();
}
