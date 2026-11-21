package com.aiinterview.platform.model.dto.request;

import java.util.List;

public record GenerateQuestionRequest(String jobTitle, String jdText, String cvText, List<FreeInterviewTurn> history) {
}