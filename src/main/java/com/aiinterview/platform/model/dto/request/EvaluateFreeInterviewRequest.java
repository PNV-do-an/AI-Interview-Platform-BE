package com.aiinterview.platform.model.dto.request;

import java.util.List;

public record EvaluateFreeInterviewRequest(String jobTitle, String jdText, List<FreeInterviewTurn> transcript) {
}