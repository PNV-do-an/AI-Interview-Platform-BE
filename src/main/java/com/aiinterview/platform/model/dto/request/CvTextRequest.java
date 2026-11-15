package com.aiinterview.platform.model.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CvTextRequest {

    @NotBlank(message = "CV text is required")
    private String cvText;
}