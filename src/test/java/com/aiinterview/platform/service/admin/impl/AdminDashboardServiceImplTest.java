package com.aiinterview.platform.service.admin.impl;

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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * GET /api/v1/admin/dashboard (spec 005 + 009) — một lần gọi phải gom đủ user/job/interview/login
 * và 12 tháng xu hướng.
 * <p>
 * Hợp đồng đáng test là những chỗ dễ vỡ khi ai đó "tối ưu" query:
 * <ul>
 *   <li>total user = tổng của 4 bucket trong MỘT aggregate row, không phải 4 lần count riêng;</li>
 *   <li>jobs.total = tổng các bucket byStatus (nên luôn khớp bảng bên cạnh);</li>
 *   <li>trends luôn đủ 12 tháng, kể cả tháng chưa có dữ liệu, và bỏ row nằm ngoài cửa sổ.</li>
 * </ul>
 */
class AdminDashboardServiceImplTest {

    private static final DateTimeFormatter MONTH_KEY = DateTimeFormatter.ofPattern("yyyy-MM");

    private UserRepository userRepository;
    private JobDescriptionRepository jobRepository;
    private InterviewSessionRepository interviewSessionRepository;
    private InterviewSlotRepository interviewSlotRepository;
    private LoginAttemptRepository loginAttemptRepository;
    private JobModerationRecordRepository moderationRecordRepository;
    private AdminDashboardServiceImpl service;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        jobRepository = mock(JobDescriptionRepository.class);
        interviewSessionRepository = mock(InterviewSessionRepository.class);
        interviewSlotRepository = mock(InterviewSlotRepository.class);
        loginAttemptRepository = mock(LoginAttemptRepository.class);
        moderationRecordRepository = mock(JobModerationRecordRepository.class);
        service = new AdminDashboardServiceImpl(userRepository, jobRepository,
                interviewSessionRepository, interviewSlotRepository,
                loginAttemptRepository, moderationRecordRepository);

        // Mặc định: DB trả dữ liệu "bình thường" để từng test chỉ override phần nó quan tâm.
        when(userRepository.countLiveMetrics(null, null)).thenReturn(List.<Object[]>of(
                new Object[]{100L, 80L, 15L, 5L}));
        when(userRepository.countDeleted(null, null)).thenReturn(3L);
        when(userRepository.countLiveByRole()).thenReturn(List.of(
                new Object[]{Role.ROLE_USER, 90L},
                new Object[]{Role.ROLE_INTERVIEWER, 8L},
                new Object[]{Role.ROLE_STAFF, 1L}));
        when(userRepository.countByCreatedAtAfterAndDeletedAtIsNull(any())).thenReturn(11L);
        when(loginAttemptRepository.countDistinctSuccessEmailsSince(any())).thenReturn(42L);
        when(jobRepository.countLiveByStatus()).thenReturn(List.of(
                new Object[]{JobStatus.OPEN, 7L},
                new Object[]{JobStatus.PENDING_APPROVAL, 2L}));
        when(jobRepository.countByCreatedAtAfterAndDeletedAtIsNull(any())).thenReturn(9L);
        when(interviewSessionRepository.count()).thenReturn(30L);
        when(interviewSessionRepository.countByCreatedAtAfter(any())).thenReturn(12L);
        when(interviewSessionRepository.countByCompletedAtAfter(any())).thenReturn(10L);
        when(interviewSessionRepository.avgOverallScore()).thenReturn(7.5);
        when(interviewSessionRepository.avgDurationSeconds()).thenReturn(new BigDecimal("125.6"));
        when(interviewSessionRepository.countByStatus(InterviewSessionStatus.IN_PROGRESS)).thenReturn(2L);
        when(interviewSessionRepository.countByMonthSince(any())).thenReturn(List.of());
        when(interviewSlotRepository.countBookedSlotsByMonthSince(any())).thenReturn(List.of());
        when(loginAttemptRepository.countBySuccessTrueAndTimestampAfter(any())).thenReturn(64L);
        when(loginAttemptRepository.countBySuccessFalseAndTimestampAfter(any())).thenReturn(6L);
    }

    @Test
    @DisplayName("User: 4 con số lấy từ một aggregate row, deleted là count riêng")
    void users_readFromSingleAggregateRow() {
        DashboardResponse.Users users = service.getDashboard(30).getUsers();

        assertThat(users.getTotal()).isEqualTo(100);
        assertThat(users.getActive()).isEqualTo(80);
        assertThat(users.getPendingVerification()).isEqualTo(15);
        assertThat(users.getLocked()).isEqualTo(5);
        assertThat(users.getDeleted()).isEqualTo(3);
        assertThat(users.getNewInPeriod()).isEqualTo(11);
        assertThat(users.getLoginsInPeriod()).isEqualTo(42);
    }

    @Test
    @DisplayName("DB chưa có user nào: aggregate row rỗng thì mọi count là 0, không nổ NPE")
    void users_emptyAggregateRowDefaultsToZero() {
        when(userRepository.countLiveMetrics(null, null)).thenReturn(List.of());

        DashboardResponse.Users users = service.getDashboard(30).getUsers();

        assertThat(users.getTotal()).isZero();
        assertThat(users.getActive()).isZero();
        assertThat(users.getPendingVerification()).isZero();
        assertThat(users.getLocked()).isZero();
    }

    @Test
    @DisplayName("User.byRole luôn đủ 4 role; role vắng mặt trong DB giữ 0")
    void users_byRoleAlwaysCoversEveryRole() {
        DashboardResponse.Users users = service.getDashboard(30).getUsers();

        assertThat(users.getByRole())
                .containsEntry("ROLE_USER", 90L)
                .containsEntry("ROLE_INTERVIEWER", 8L)
                .containsEntry("ROLE_STAFF", 1L)
                .containsEntry("ROLE_ADMIN", 0L);
    }

    @Test
    @DisplayName("jobs.total = tổng byStatus nên hai con số cạnh nhau không bao giờ lệch")
    void jobs_totalIsSumOfStatusBuckets() {
        DashboardResponse.Jobs jobs = service.getDashboard(30).getJobs();

        assertThat(jobs.getByStatus()).containsEntry("OPEN", 7L).containsEntry("PENDING_APPROVAL", 2L);
        assertThat(jobs.getTotal()).isEqualTo(9);
        assertThat(jobs.getNewInPeriod()).isEqualTo(9);
    }

    @Test
    @DisplayName("byStatus luôn đủ 4 trạng thái (DRAFT/OPEN... = 0) để chart không thiếu cột")
    void jobs_byStatusAlwaysCoversEveryStatus() {
        DashboardResponse.Jobs jobs = service.getDashboard(30).getJobs();

        assertThat(jobs.getByStatus()).containsOnlyKeys(
                "DRAFT", "PENDING_APPROVAL", "OPEN", "CLOSED");
        assertThat(jobs.getByStatus()).containsEntry("DRAFT", 0L).containsEntry("CLOSED", 0L);
    }

    @Test
    @DisplayName("pendingModeration dùng chung một nguồn với byStatus.PENDING_APPROVAL")
    void moderation_pendingApprovalMatchesStatusBucket() {
        DashboardResponse response = service.getDashboard(30);

        assertThat(response.getJobs().getPendingModeration()).isEqualTo(2);
        assertThat(response.getModeration().getPendingApproval()).isEqualTo(2);
    }

    @Test
    @DisplayName("Interview: avgDurationSeconds được làm tròn về giây (BigDecimal 125.6 -> 126)")
    void interviews_roundsAverageDuration() {
        DashboardResponse.Interviews interviews = service.getDashboard(30).getInterviews();

        assertThat(interviews.getTotal()).isEqualTo(30);
        assertThat(interviews.getInPeriod()).isEqualTo(12);
        assertThat(interviews.getCompletedInPeriod()).isEqualTo(10);
        assertThat(interviews.getAvgOverallScore()).isEqualTo(7.5);
        assertThat(interviews.getAvgDurationSeconds()).isEqualTo(126L);
        assertThat(interviews.getLiveNow()).isEqualTo(2);
    }

    @Test
    @DisplayName("Chưa có buổi nào: avgDuration = null (không phải 0), FE hiện dấu gạch")
    void interviews_nullAverageDurationStaysNull() {
        when(interviewSessionRepository.avgDurationSeconds()).thenReturn(null);
        when(interviewSessionRepository.avgOverallScore()).thenReturn(null);

        DashboardResponse.Interviews interviews = service.getDashboard(30).getInterviews();

        assertThat(interviews.getAvgDurationSeconds()).isNull();
        assertThat(interviews.getAvgOverallScore()).isNull();
    }

    @Test
    @DisplayName("Logins tách đúng success/failed trong khoảng thời gian")
    void logins_splitSuccessAndFailed() {
        DashboardResponse.Logins logins = service.getDashboard(30).getLogins();

        assertThat(logins.getSuccessInPeriod()).isEqualTo(64);
        assertThat(logins.getFailedInPeriod()).isEqualTo(6);
    }

    @Test
    @DisplayName("Trends luôn đủ 12 tháng, tăng dần, kết thúc ở tháng hiện tại")
    void trends_alwaysTwelveAscendingMonthsEndingNow() {
        List<DashboardResponse.Month> months = service.getDashboard(30).getTrends().getMonths();

        YearMonth now = YearMonth.now();
        assertThat(months).hasSize(12);
        assertThat(months.get(0).getMonth()).isEqualTo(now.minusMonths(11).format(MONTH_KEY));
        assertThat(months.get(11).getMonth()).isEqualTo(now.format(MONTH_KEY));
        assertThat(months).extracting(DashboardResponse.Month::getMonth).isSorted();
        assertThat(months).allSatisfy(m -> {
            assertThat(m.getMock()).isZero();
            assertThat(m.getFormal()).isZero();
        });
    }

    @Test
    @DisplayName("Trends: mock = số buổi AI, formal = số slot đã đặt, không trộn cột")
    void trends_placeMockAndFormalInSeparateColumns() {
        YearMonth now = YearMonth.now();
        when(interviewSessionRepository.countByMonthSince(any())).thenReturn(List.<Object[]>of(
                new Object[]{now.format(MONTH_KEY), 4L}));
        when(interviewSlotRepository.countBookedSlotsByMonthSince(any())).thenReturn(List.<Object[]>of(
                new Object[]{now.format(MONTH_KEY), 6L},
                new Object[]{now.minusMonths(11).format(MONTH_KEY), 2L}));

        List<DashboardResponse.Month> months = service.getDashboard(30).getTrends().getMonths();

        DashboardResponse.Month current = months.get(11);
        assertThat(current.getMock()).isEqualTo(4);
        assertThat(current.getFormal()).isEqualTo(6);
        assertThat(months.get(0).getFormal()).isEqualTo(2);
        assertThat(months.get(0).getMock()).isZero();
    }

    @Test
    @DisplayName("Trends: row nằm ngoài cửa sổ 12 tháng bị bỏ qua, không tạo bucket mới")
    void trends_ignoresRowsOutsideWindow() {
        when(interviewSessionRepository.countByMonthSince(any())).thenReturn(List.<Object[]>of(
                new Object[]{"2001-01", 99L},
                new Object[]{null, 5L}));

        List<DashboardResponse.Month> months = service.getDashboard(30).getTrends().getMonths();

        assertThat(months).hasSize(12);
        assertThat(months).extracting(DashboardResponse.Month::getMonth)
                .doesNotContain("2001-01");
        assertThat(months).allSatisfy(m -> assertThat(m.getMock()).isZero());
    }

    @Test
    @DisplayName("rangeDays chỉ đổi cửa sổ 'trong khoảng': 7 ngày -> since ≈ now-7d")
    void rangeDays_appliedToSinceWindow() {
        ArgumentCaptor<LocalDateTime> since = ArgumentCaptor.forClass(LocalDateTime.class);

        DashboardResponse response = service.getDashboard(7);

        assertThat(response.getRangeDays()).isEqualTo(7);
        verify(userRepository).countByCreatedAtAfterAndDeletedAtIsNull(since.capture());
        assertThat(Duration.between(since.getValue(), LocalDateTime.now()).toDays()).isEqualTo(7);
        verify(jobRepository).countByCreatedAtAfterAndDeletedAtIsNull(any());
        verify(interviewSessionRepository).countByCreatedAtAfter(any());
        verify(interviewSessionRepository).countByCompletedAtAfter(any());
    }

    @Test
    @DisplayName("Trends đọc thẳng bảng sự kiện, không dùng bảng tổng hợp moderation")
    void trends_doNotTouchModerationTable() {
        service.getDashboard(30);

        verify(moderationRecordRepository, org.mockito.Mockito.never()).findAll();
        verify(interviewSessionRepository).countByMonthSince(any());
        verify(interviewSlotRepository).countBookedSlotsByMonthSince(any());
    }
}
