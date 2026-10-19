package com.aiinterview.platform.service.impl;

import com.aiinterview.platform.model.entity.AdminAuditLog;
import com.aiinterview.platform.model.entity.User;
import com.aiinterview.platform.model.enums.AuditAction;
import com.aiinterview.platform.model.repository.AdminAuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Writes rejected admin attempts in their own transaction: the caller's transaction is rolled back
 * by the rethrown exception, so a plain save() would disappear with it.
 */
@Component
@RequiredArgsConstructor
public class AdminAuditWriter {

    private final AdminAuditLogRepository auditLogRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void writeRejected(User admin, Long targetUserId, AuditAction action, String reason) {
        auditLogRepository.save(AdminAuditLog.builder()
                .adminId(admin.getId())
                .adminEmail(admin.getEmail())
                .targetUserId(targetUserId)
                .action(action.name())
                .detail("Nỗ lực bị từ chối: " + action.name())
                .succeeded(false)
                .reason(reason)
                .build());
    }
}
