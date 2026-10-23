package com.aiinterview.platform.model.enums;

/** Lifecycle of one JD moderation record (spec 004 FR-003). */
public enum ModerationAction {
    SUBMITTED,
    RESUBMITTED,
    /** Employer pulled the JD back out of the review queue before anyone decided. */
    WITHDRAWN,
    APPROVED,
    REJECTED
}
