package com.aiinterview.platform.model.dto.response;

import lombok.Builder;
import lombok.Data;

/** GET /api/v1/admin/jobs/moderation/stats — the four chips on top of the queue (spec 009 FR-004). */
@Data
@Builder
public class ModerationQueueStatsResponse {

    /** Jobs currently waiting for a decision (status = PENDING_APPROVAL, not soft-deleted). */
    private long pending;
    /** Drafts: never submitted, or sent back by a reviewer (rejection puts a job back to DRAFT). */
    private long draft;
    /** Open jobs — in this flow a job only goes live after an approval. */
    private long approved;
    /** Distinct jobs that have at least one REJECTED decision. Overlaps draft on purpose. */
    private long rejected;
    /** Live jobs in every status — equals jobs.total on the dashboard. */
    private long total;
}
