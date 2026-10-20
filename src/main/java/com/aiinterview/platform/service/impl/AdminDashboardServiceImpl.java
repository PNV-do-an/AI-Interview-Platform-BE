package com.aiinterview.platform.service.impl;

import com.aiinterview.platform.model.dto.response.DashboardResponse;
import com.aiinterview.platform.model.enums.JobStatus;
import com.aiinterview.platform.model.enums.Role;
import com.aiinterview.platform.model.repository.InterviewSessionRepository;
import com.aiinterview.platform.model.repository.JobDescriptionRepository;
import com.aiinterview.platform.model.repository.JobModerationRecordRepository;
import com.aiinterview.platform.model.repository.LoginAttemptRepository;
import com.aiinterview.platform.model.repository.UserRepository;
import com.aiinterview.platform.service.AdminDashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AdminDashboardServiceImpl implements AdminDashboardService {

    private final UserRepository userRepository;
    private final JobDescriptionRepository jobRepository;
    private final InterviewSessionRepository interviewSessionRepository;
    private final LoginAttemptRepository loginAttemptRepository;
    private final JobModerationRecordRepository moderationRecordRepository;

    @Override
    @Transactional(readOnly = true)
    public DashboardResponse getDashboard(int rangeDays) {
        LocalDateTime since = LocalDateTime.now().minusDays(rangeDays);

        // Reuse the exact predicate of the user list (spec 005 FR-003) via countLiveMetrics.
        List<Object[]> metricRows = userRepository.countLiveMetrics(null, null);
        Object[] live = metricRows.isEmpty() ? new Object[0] : metricRows.get(0);

        Map<String, Long> byRole = emptyRoleCounts();
        for (Object[] row : userRepository.countLiveByRole()) {
            Role role = (Role) row[0];
            byRole.put(role.name(), asLong(row[1]));
        }

        Map<String, Long> byStatus = emptyStatusCounts();
        for (Object[] row : jobRepository.countLiveByStatus()) {
            JobStatus status = (JobStatus) row[0];
            byStatus.put(status.name(), asLong(row[1]));
        }

        long pendingModeration = byStatus.get(JobStatus.PENDING_APPROVAL.name());

        return DashboardResponse.builder()
                .rangeDays(rangeDays)
                .users(DashboardResponse.Users.builder()
                        .total(asLong(at(live, 0)))
                        .active(asLong(at(live, 1)))
                        .pendingVerification(asLong(at(live, 2)))
                        .locked(asLong(at(live, 3)))
                        .deleted(userRepository.countDeleted(null, null))
                        .byRole(byRole)
                        .newInPeriod(userRepository.countByCreatedAtAfterAndDeletedAtIsNull(since))
                        .loginsInPeriod(loginAttemptRepository.countDistinctSuccessEmailsSince(since))
                        .build())
                .jobs(DashboardResponse.Jobs.builder()
                        .total(byStatus.values().stream().mapToLong(Long::longValue).sum())
                        .byStatus(byStatus)
                        .pendingModeration(pendingModeration)
                        .newInPeriod(jobRepository.countByCreatedAtAfterAndDeletedAtIsNull(since))
                        .build())
                .interviews(DashboardResponse.Interviews.builder()
                        .total(interviewSessionRepository.count())
                        .inPeriod(interviewSessionRepository.countByCreatedAtAfter(since))
                        .completedInPeriod(interviewSessionRepository.countByCompletedAtAfter(since))
                        .avgOverallScore(interviewSessionRepository.avgOverallScore())
                        .build())
                .logins(DashboardResponse.Logins.builder()
                        .successInPeriod(loginAttemptRepository.countBySuccessTrueAndTimestampAfter(since))
                        .failedInPeriod(loginAttemptRepository.countBySuccessFalseAndTimestampAfter(since))
                        .build())
                .moderation(DashboardResponse.Moderation.builder()
                        .pendingApproval(pendingModeration)
                        .build())
                .build();
    }

    private static Map<String, Long> emptyRoleCounts() {
        Map<String, Long> map = new LinkedHashMap<>();
        Arrays.stream(Role.values()).forEach(role -> map.put(role.name(), 0L));
        return map;
    }

    private static Map<String, Long> emptyStatusCounts() {
        Map<String, Long> map = new LinkedHashMap<>();
        Arrays.stream(JobStatus.values()).forEach(status -> map.put(status.name(), 0L));
        return map;
    }

    private static long asLong(Object value) {
        return value instanceof Number number ? number.longValue() : 0L;
    }

    private static Object at(Object[] row, int index) {
        return index < row.length ? row[index] : null;
    }
}
