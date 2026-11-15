package com.aiinterview.platform.model.dto.response;

import com.aiinterview.platform.model.enums.DifficultyLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TopicResponse {

    private Long id;
    private String title;
    private String description;
    private Integer topicOrder;
    private DifficultyLevel difficulty;
    private String skillTag;
    private Integer totalQuestions;
    private Integer completedQuestions;
}