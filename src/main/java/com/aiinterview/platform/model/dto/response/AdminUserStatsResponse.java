package com.aiinterview.platform.model.dto.response;

/**
 * Tab badges for the admin user directory. Every number is counted in the DB with the same filter
 * as {@code GET /api/v1/admin/users}, so a badge can never disagree with the table under it.
 *
 * <p>{@code activeLast30Days} is derived from {@code login_attempts} (Clarify C-1): it means
 * "signed in successfully in the last 30 days", not "was active in the last 30 days".
 */
public record AdminUserStatsResponse(
        long total,
        long active,
        long pendingVerification,
        long locked,
        long disabled,
        long deleted,
        long activeLast30Days
) {
}
