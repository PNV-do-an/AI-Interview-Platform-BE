package com.aiinterview.platform.service.impl;

import com.aiinterview.platform.model.dto.response.DashboardResponse;
import com.aiinterview.platform.model.enums.InterviewSessionStatus;
import com.aiinterview.platform.model.enums.JobStatus;
import com.aiinterview.platform.model.enums.Role;
import com.aiinterview.platform.model.repository.InterviewSessionRepository;
import com.aiinterview.platform.model.repository.InterviewSlotRepository;
import com.aiinterview.platform.model.repository.JobDescriptionRepository;
import com.aiinterview.platform.model.repository.JobModerationRecordRepository;
import com.aiinterview.platform.model.repository.LoginAttemptRepository;
import com.aiinterview.platform.model.repository.UserRepository;
import com.aiinterview.platform.service.AdminDashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AdminDashboardServiceImpl implements AdminDashboardService {

    private static final int TREND_MONTHS = 12;
    private static final DateTimeFormatter MONTH_KEY = DateTimeFormatter.ofPattern("yyyy-MM");

    private final UserRepository userRepository;
    private final JobDescriptionRepository jobRepository;
    private final InterviewSessionRepository interviewSessionRepository;
    private final InterviewSlotRepository interviewSlotRepository;
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
                        .avgDurationSeconds(rounded(interviewSessionRepository.avgDurationSeconds()))
                        .liveNow(interviewSessionRepository.countByStatus(InterviewSessionStatus.IN_PROGRESS))
                        .build())
                .logins(DashboardResponse.Logins.builder()
                        .successInPeriod(loginAttemptRepository.countBySuccessTrueAndTimestampAfter(since))
                        .failedInPeriod(loginAttemptRepository.countBySuccessFalseAndTimestampAfter(since))
                        .build())
                .moderation(DashboardResponse.Moderation.builder()
                        .pendingApproval(pendingModeration)
                        .build())
                .trends(monthlyTrends())
                .build();
    }

    /** Two group-by reads over the event tables, no aggregate table (009 FR-001). */
    private DashboardResponse.Trends monthlyTrends() {
        LocalDateTime from = YearMonth.from(LocalDate.now())
                .minusMonths(TREND_MONTHS - 1L)
                .atDay(1)
                .atStartOfDay();

        Map<String, long[]> buckets = new LinkedHashMap<>();
        YearMonth cursor = YearMonth.from(from);
        for (int i = 0; i < TREND_MONTHS; i++) {
            buckets.put(cursor.format(MONTH_KEY), new long[2]);
            cursor = cursor.plusMonths(1);
        }

        fill(buckets, interviewSessionRepository.countByMonthSince(from), 0);
        fill(buckets, interviewSlotRepository.countBookedSlotsByMonthSince(from), 1);

        List<DashboardResponse.Month> months = new ArrayList<>();
        buckets.forEach((key, counts) -> months.add(DashboardResponse.Month.builder()
                .month(key).mock(counts[0]).formal(counts[1]).build()));
        return DashboardResponse.Trends.builder().months(months).build();
    }

    /** Buckets outside the window (e.g. a row written days earlier in the same month) are ignored. */
    private static void fill(Map<String, long[]> buckets, List<Object[]> rows, int column) {
        for (Object[] row : rows) {
            long[] cell = buckets.get(String.valueOf(row[0]));
            if (cell != null) {
                cell[column] = asLong(row[1]);
            }
        }
    }

    private static Long rounded(BigDecimal value) {
        return value == null ? null : Math.round(value.doubleValue());
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
