package com.aiinterview.platform.model.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class QuizQuestionResponse {
    private String question;
    /** Alias for clients expecting a different field name. */
    private String text;
    private List<String> options;
    /** Alias for clients expecting a different field name. */
    private List<String> choices;
}
