package com.aiinterview.platform.model.dto.response;

import java.util.Map;

public record EvaluateFreeInterviewResponse(Map<String, Integer> rubric, String strengths, String improvements, String rubricVersion) {
}