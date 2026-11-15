package com.aiinterview.platform.model.dto.response;

import com.aiinterview.platform.model.enums.DifficultyLevel;
import com.aiinterview.platform.model.enums.ProgramStatus;
import com.aiinterview.platform.model.enums.ProgramType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StudyProgramResponse {

    private Long id;
    private String title;
    private String description;
    private ProgramType programType;
    private String targetPosition;
    private DifficultyLevel difficultyLevel;
    private ProgramStatus status;
    private Integer totalTopics;
    private Integer completedTopics;
    private LocalDateTime createdAt;
    private LocalDateTime completedAt;
}