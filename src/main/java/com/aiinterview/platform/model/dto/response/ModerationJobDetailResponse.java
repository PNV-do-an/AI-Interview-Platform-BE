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
    /** Stored value. Null for jobs submitted before spec 009 filled the column with rule scores. */
    private Integer aiSafetyScore;
    /** Recomputed from the same rules, so it always carries a score + the reasons (009 FR-006). */
    private AiSafety aiSafety;
    /** Who posted the job and how their history looks (009 FR-005). */
    private Employer employer;
    /** Newest first (spec 004 FR-002). */
    private List<ModerationRecordResponse> timeline;

    @Data
    @Builder
    public static class AiSafety {
        private int score;
        /** Fixed order: title, description, requirements, contact leaks. */
        private List<SafetyCheck> checks;
    }

    @Data
    @Builder
    public static class SafetyCheck {
        private String key;
        private String label;
        private boolean passed;
        /** Why the check passed or failed, in Vietnamese, for the reviewer card. */
        private String note;
    }

    @Data
    @Builder
    public static class Employer {
        private Long id;
        private String companyName;
        private String email;
        private LocalDateTime accountCreatedAt;
        private EmployerJobs jobs;
        /** 100 × approved / (approved + rejected), rounded; null when the employer has no decision yet. */
        private Integer trustScore;
    }

    @Data
    @Builder
    public static class EmployerJobs {
        private long total;
        private long approved;
        private long rejected;
        private long pending;
    }
}
