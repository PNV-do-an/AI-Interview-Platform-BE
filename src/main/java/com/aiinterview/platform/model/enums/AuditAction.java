package com.aiinterview.platform.model.enums;

/** Closed set of admin actions recorded in {@code admin_audit_log.action}. */
public enum AuditAction {
    VIEW_USER,
    LOCK_USER,
    UNLOCK_USER,
    ENABLE_USER,
    DISABLE_USER,
    DELETE_USER,
    RESTORE_USER,
    RESET_PASSWORD,
    CHANGE_ROLE,
    INVITE_USER,
    RESEND_INVITATION,
    REVOKE_INVITATION,
    UPDATE_PROFILE,
    EXPORT_USERS,
    APPROVE_JOB,
    REJECT_JOB,
    UPDATE_SETTINGS
}
