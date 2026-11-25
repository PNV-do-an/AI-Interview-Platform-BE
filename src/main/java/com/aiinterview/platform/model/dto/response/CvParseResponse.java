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

    /** Vi tri phu hop, tinh tu catalog do admin quan ly (so khop so ky nang trong CV). */
    @Builder.Default
    private List<PositionRecommendationResponse> recommendedPositions = new ArrayList<>();

    private String targetPosition;
    private String targetLevel;

    /** Vị trí user chọn trước khi upload; null = không chọn, chạy như luồng cũ. */
    private Long targetPositionId;

    /**
     * Điểm khớp của đúng vị trí đích. Khác recommendedPositions ở chỗ không bị lọc bởi
     * "matched > 0" nên FE luôn hiển thị được vị trí user đã chọn dù nó kém.
     */
    private PositionRecommendationResponse targetPositionMatch;

    /** Mã cảnh báo cho FE xử lý; hiện tại chỉ có POSITION_MISMATCH. */
    @Builder.Default
    private List<String> warnings = new ArrayList<>();

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