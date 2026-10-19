package com.aiinterview.platform.model.dto.response;

import com.aiinterview.platform.model.entity.AdminAuditLog;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class AuditLogResponse {

    private Long id;
    private Long adminId;
    private String adminEmail;
    private Long targetUserId;
    /** Filled by the service from the users table; null when the target no longer exists. */
    private String targetEmail;
    private String targetFullName;
    private String action;
    private String detail;
    /** false = the attempt was rejected by a business rule. */
    private boolean succeeded;
    private String reason;
    private LocalDateTime createdAt;

    public static AuditLogResponse from(AdminAuditLog log) {
        return AuditLogResponse.builder()
                .id(log.getId())
                .adminId(log.getAdminId())
                .adminEmail(log.getAdminEmail())
                .targetUserId(log.getTargetUserId())
                .action(log.getAction())
                .detail(log.getDetail())
                .succeeded(log.isSucceeded())
                .reason(log.getReason())
                .createdAt(log.getCreatedAt())
                .build();
    }
}
