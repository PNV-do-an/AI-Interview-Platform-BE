package com.aiinterview.platform.model.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class SetPositionRequest {

    @NotBlank(message = "Position is required")
    private String position;

    @NotBlank(message = "Level is required")
    private String level;

    /**
     * Id vị trí trong catalog. Khi có, title của catalog được ghi đè lên {@code position}
     * để hai cột target_position / target_position_id không mâu thuẫn.
     */
    private Long positionId;
}