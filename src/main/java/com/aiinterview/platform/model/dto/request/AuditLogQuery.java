package com.aiinterview.platform.model.dto.request;

import com.aiinterview.platform.model.enums.AuditAction;

import java.time.LocalDateTime;

/** Filter + paging parameters for {@code GET /api/v1/admin/audit-logs}. */
public record AuditLogQuery(
        int page,
        int size,
        Long adminId,
        Long targetUserId,
        AuditAction action,
        LocalDateTime createdFrom,
        LocalDateTime createdTo
) {
}
