package com.aiinterview.platform.model.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class SetPositionRequest {

    @NotBlank(message = "Position is required")
    private String position;

    @NotBlank(message = "Level is required")
    private String level;
}