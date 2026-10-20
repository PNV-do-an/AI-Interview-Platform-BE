package com.aiinterview.platform.model.dto.response;

import lombok.Builder;
import lombok.Data;

/** Aggregate cards of the moderation history screen (spec 004 FR-007). */
@Builder
public record ModerationHistoryStatsResponse(long totalDecisions,
                                             long approved,
                                             long rejected,
                                             Double avgProcessingSeconds) {
}
