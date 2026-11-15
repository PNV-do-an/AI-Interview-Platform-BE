package com.aiinterview.platform.model.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class SubmitInterviewAnswerRequest {

    @NotNull(message = "Question ID is required")
    private Long questionId;

    @NotBlank(message = "Text answer is required")
    private String textAnswer;

    private String selectedOption;

    private String audioBase64;

    private String audioFileName;
}