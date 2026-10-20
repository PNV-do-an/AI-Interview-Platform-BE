package com.aiinterview.platform.model.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/** Full moderation view of one job: the standard detail plus console-only fields + timeline. */
@Data
@Builder
public class ModerationJobDetailResponse {

    private JobDetailResponse job;
    private LocalDateTime submittedAt;
    private LocalDateTime decidedAt;
    private Integer aiSafetyScore;
    /** Newest first (spec 004 FR-002). */
    private List<ModerationRecordResponse> timeline;
}
