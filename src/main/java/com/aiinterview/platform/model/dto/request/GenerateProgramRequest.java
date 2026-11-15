package com.aiinterview.platform.model.dto.request;

import com.aiinterview.platform.model.enums.ProgramType;
import lombok.Data;

@Data
public class GenerateProgramRequest {

    private ProgramType programType;

    private String targetPosition;

    private Integer questionsPerTopic;
}