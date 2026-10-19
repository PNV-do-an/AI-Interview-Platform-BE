package com.aiinterview.platform.model.enums;

/** Status values accepted by {@code GET /api/v1/admin/users?status=...}. */
public enum UserStatusFilter {
    ACTIVE,
    INACTIVE,
    LOCKED,
    /** Created but has never clicked the verification link ({@code verification_token IS NOT NULL}). */
    PENDING,
    DELETED
}
