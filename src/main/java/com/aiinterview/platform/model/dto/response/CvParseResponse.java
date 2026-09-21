package com.aiinterview.platform.model.dto.response;

import com.aiinterview.platform.model.enums.ProficiencyLevel;
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
public class CvParseResponse {

    private Long cvId;
    private String fileName;

    @Builder.Default
    private List<SkillData> skills = new ArrayList<>();

    @Builder.Default
    private List<ExperienceData> experience = new ArrayList<>();

    @Builder.Default
    private List<EducationData> education = new ArrayList<>();

    @Builder.Default
    private List<String> suggestedPositions = new ArrayList<>();

    @Builder.Default
    private List<String> knowledgeGaps = new ArrayList<>();

    private String targetPosition;
    private String targetLevel;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SkillData {
        private String name;
        private ProficiencyLevel proficiency;
        private Integer years;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ExperienceData {
        private String company;
        private String role;
        private String duration;
        @Builder.Default
        private List<String> achievements = new ArrayList<>();
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EducationData {
        private String institution;
        private String degree;
        private Integer year;
    }
}