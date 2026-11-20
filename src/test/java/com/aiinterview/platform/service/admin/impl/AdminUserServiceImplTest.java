package com.aiinterview.platform.service.admin.impl;

import com.aiinterview.platform.common.exception.BadRequestException;
import com.aiinterview.platform.common.exception.EmailAlreadyRegisteredException;
import com.aiinterview.platform.common.exception.ResourceNotFoundException;
import com.aiinterview.platform.model.dto.request.AdminInviteUserRequest;
import com.aiinterview.platform.model.dto.request.AdminUpdateProfileRequest;
import com.aiinterview.platform.model.dto.request.AuditLogQuery;
import com.aiinterview.platform.model.dto.request.BulkRoleChangeRequest;
import com.aiinterview.platform.model.dto.request.BulkUserActionRequest;
import com.aiinterview.platform.model.dto.request.LockAccountRequest;
import com.aiinterview.platform.model.dto.response.AdminProfileUpdateResponse;
import com.aiinterview.platform.model.dto.response.AdminUserStatsResponse;
import com.aiinterview.platform.model.dto.response.AuditLogResponse;
import com.aiinterview.platform.model.dto.response.BulkUserActionResult;
import com.aiinterview.platform.model.dto.response.UserActivityResponse;
import com.aiinterview.platform.model.dto.response.UserDetailResponse;
import com.aiinterview.platform.model.dto.response.UserSummaryResponse;
import com.aiinterview.platform.model.entity.AdminAuditLog;
import com.aiinterview.platform.model.entity.Application;
import com.aiinterview.platform.model.entity.CandidateProfile;
import com.aiinterview.platform.model.entity.InterviewSession;
import com.aiinterview.platform.model.entity.InterviewSlot;
import com.aiinterview.platform.model.entity.LoginAttempt;
import com.aiinterview.platform.model.entity.User;
import com.aiinterview.platform.model.enums.ApplicationStatus;
import com.aiinterview.platform.model.enums.AuditAction;
import com.aiinterview.platform.model.enums.InterviewSessionStatus;
import com.aiinterview.platform.model.enums.Role;
import com.aiinterview.platform.model.enums.UserStatusFilter;
import com.aiinterview.platform.model.repository.AdminAuditLogRepository;
import com.aiinterview.platform.model.repository.ApplicationRepository;
import com.aiinterview.platform.model.repository.CandidateProfileRepository;
import com.aiinterview.platform.model.repository.InterviewSessionRepository;
import com.aiinterview.platform.model.repository.InterviewSlotRepository;
import com.aiinterview.platform.model.repository.LoginAttemptRepository;
import com.aiinterview.platform.model.repository.RecoveryCodeRepository;
import com.aiinterview.platform.model.repository.UserRepository;
import com.aiinterview.platform.service.admin.AdminAuditWriter;
import com.aiinterview.platform.service.admin.AdminUserService;
import com.aiinterview.platform.service.auth.UserSessionService;
import com.aiinterview.platform.service.infra.EmailService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * AdminUserService — bảng điều khiển người dùng (spec 001/002/008/009). Đây là service lớn nhất của
 * module admin (893 dòng, 14 phụ thuộc) nên test ở đây tập trung vào <b>hợp đồng nghiệp vụ</b> mà
 * controller/Postman không nhìn thấy:
 * <ul>
 *   <li>auto-guard: không ai được tự khóa/tự xóa/tự hạ quyền chính mình, không được để hệ thống
 *       hết admin, không được khóa tài khoản ROLE_ADMIN;</li>
 *   <li>side-effect khi thay đổi trạng thái: tokenVersion +1 và revokeAll() để mọi JWT cũ chết ngay,
 *       vì nếu thiếu thì user bị khóa vẫn dùng được token 15 phút;</li>
 *   <li>attempt(): hành vi bị từ chối vẫn phải để lại audit log succeeded=false;</li>
 *   <li>CSV export: chống formula injection và quote đúng cột có dấu phẩy/ngoặc kép.</li>
 * </ul>
 */
class AdminUserServiceImplTest {

    private static final long ADMIN_ID = 1L;
    private static final long MEMBER_ID = 5L;
    private static final String ADMIN_EMAIL = "admin@aiinterview.vn";
    private static final String MEMBER_EMAIL = "member@example.com";

    private UserRepository userRepository;
    private AdminAuditLogRepository auditLogRepository;
    private LoginAttemptRepository loginAttemptRepository;
    private RecoveryCodeRepository recoveryCodeRepository;
    private CandidateProfileRepository candidateProfileRepository;
    private ApplicationRepository applicationRepository;
    private InterviewSessionRepository interviewSessionRepository;
    private InterviewSlotRepository interviewSlotRepository;
    private EmailService emailService;
    private AdminAuditWriter auditWriter;
    private PasswordEncoder passwordEncoder;
    private UserSessionService userSessionService;
    private ObjectProvider<AdminUserService> selfProvider;
    private AdminUserServiceImpl service;

    private User admin;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        auditLogRepository = mock(AdminAuditLogRepository.class);
        loginAttemptRepository = mock(LoginAttemptRepository.class);
        recoveryCodeRepository = mock(RecoveryCodeRepository.class);
        candidateProfileRepository = mock(CandidateProfileRepository.class);
        applicationRepository = mock(ApplicationRepository.class);
        interviewSessionRepository = mock(InterviewSessionRepository.class);
        interviewSlotRepository = mock(InterviewSlotRepository.class);
        emailService = mock(EmailService.class);
        auditWriter = mock(AdminAuditWriter.class);
        passwordEncoder = mock(PasswordEncoder.class);
        userSessionService = mock(UserSessionService.class);
        selfProvider = mock(ObjectProvider.class);

        service = new AdminUserServiceImpl(userRepository, auditLogRepository, loginAttemptRepository,
                recoveryCodeRepository, candidateProfileRepository, applicationRepository,
                interviewSessionRepository, interviewSlotRepository, emailService, auditWriter,
                passwordEncoder, userSessionService, selfProvider);
        // Bulk actions go through the self-proxy; in a unit test the plain instance stands in for it.
        when(selfProvider.getObject()).thenReturn(service);

        admin = User.builder()
                .id(ADMIN_ID)
                .email(ADMIN_EMAIL)
                .fullName("Quản trị viên")
                .role(Role.ROLE_ADMIN)
                .enabled(true)
                .build();
        loginAs(admin);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    // ── đọc danh sách ────────────────────────────────────────────────────────

    @Test
    @DisplayName("Danh sách live không nhận filter DELETED: phải chỉ sang endpoint /users/deleted")
    void getUsers_rejectsDeletedFilter() {
        assertThatThrownBy(() -> service.getUsers(0, 20, null, UserStatusFilter.DELETED, null))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("/api/v1/admin/users/deleted");
    }

    @Test
    @DisplayName("Từ khóa tìm kiếm được escape %/_ và kèm lastLoginAt của từng dòng (khớp không phân biệt hoa thường)")
    void getUsers_escapesSearchAndJoinsLastLogin() {
        User member = member(MEMBER_ID, MEMBER_EMAIL);
        LocalDateTime lastLogin = LocalDateTime.of(2026, 11, 1, 9, 30);
        when(userRepository.findAllWithFilters(any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(member), Pageable.unpaged(), 1));
        when(loginAttemptRepository.findLastSuccessByEmailIn(List.of(MEMBER_EMAIL)))
                .thenReturn(List.<Object[]>of(new Object[]{"MEMBER@EXAMPLE.COM", lastLogin}));

        Page<UserSummaryResponse> page = service.getUsers(0, 20, "  50%_off ", UserStatusFilter.ACTIVE, null);

        ArgumentCaptor<String> search = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> status = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(userRepository).findAllWithFilters(search.capture(), eq(null), status.capture(), pageable.capture());
        assertThat(search.getValue()).isEqualTo("50\\%\\_off");
        assertThat(status.getValue()).isEqualTo("ACTIVE");
        assertThat(pageable.getValue().getSort()).isEqualTo(Sort.by(Sort.Direction.DESC, "createdAt"));
        assertThat(page.getContent().get(0).getLastLoginAt()).isEqualTo(lastLogin);
        assertThat(page.getContent().get(0).getStatus()).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("Từ khóa dài quá 100 ký tự bị chặn trước khi chạm DB")
    void getUsers_rejectsOverlongSearch() {
        assertThatThrownBy(() -> service.getUsers(0, 20, "x".repeat(101), null, null))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("tối đa 100 ký tự");
        verifyNoInteractions(userRepository);
    }

    @Test
    @DisplayName("Tab Đã xóa dùng query riêng, không truyền status")
    void getDeletedUsers_usesDeletedQuery() {
        when(userRepository.findDeletedWithFilters(eq(null), eq(null), any()))
                .thenReturn(new PageImpl<>(List.of(member(MEMBER_ID, MEMBER_EMAIL))));

        Page<UserSummaryResponse> page = service.getDeletedUsers(0, 10, null, null);

        assertThat(page.getContent()).hasSize(1);
        verify(userRepository).findDeletedWithFilters(eq(null), eq(null), any());
    }

    @Test
    @DisplayName("Stats: 5 số lấy từ MỘT row aggregate, deleted và activeLast30Days là 2 count riêng")
    void getStats_mapsAggregateRowAndSeparateCounts() {
        when(userRepository.countLiveMetrics(null, Role.ROLE_USER))
                .thenReturn(List.<Object[]>of(new Object[]{120L, 90L, 12L, 6L, 132L}));
        when(userRepository.countDeleted(null, Role.ROLE_USER)).thenReturn(4L);
        when(userRepository.countWithRecentLogin(eq(null), eq(Role.ROLE_USER), any())).thenReturn(55L);

        AdminUserStatsResponse stats = service.getStats(null, Role.ROLE_USER);

        assertThat(stats.total()).isEqualTo(120);
        assertThat(stats.active()).isEqualTo(90);
        assertThat(stats.pendingVerification()).isEqualTo(12);
        assertThat(stats.locked()).isEqualTo(6);
        assertThat(stats.disabled()).isEqualTo(132);
        assertThat(stats.deleted()).isEqualTo(4);
        assertThat(stats.activeLast30Days()).isEqualTo(55);
    }

    @Test
    @DisplayName("Stats: row aggregate bị cắt ngắn (DB hiếm) thì các cột thiếu là 0, không NPE")
    void getStats_toleratesTruncatedRow() {
        when(userRepository.countLiveMetrics(null, null))
                .thenReturn(List.<Object[]>of(new Object[]{10L, 8L}));

        AdminUserStatsResponse stats = service.getStats(null, null);

        assertThat(stats.total()).isEqualTo(10);
        assertThat(stats.disabled()).isZero();
        assertThat(stats.deleted()).isZero();
    }

    @Test
    @DisplayName("Chi tiết user: gộp login gần nhất, điểm AI làm tròn 1 số lẻ, đếm hoạt động, tách skills và ghi audit")
    void getUserDetail_enrichesAllBlocks() {
        User member = member(MEMBER_ID, MEMBER_EMAIL);
        LocalDateTime loginAt = LocalDateTime.of(2026, 10, 20, 8, 0);
        // LoginAttempt chỉ có constructor (email, ip, success) và tự gán timestamp = now().
        LoginAttempt attempt = new LoginAttempt(MEMBER_EMAIL, "203.0.113.9", true);
        when(userRepository.findById(MEMBER_ID)).thenReturn(Optional.of(member));
        when(loginAttemptRepository.findTop1ByEmailAndSuccessTrueOrderByTimestampDesc(MEMBER_EMAIL))
                .thenReturn(Optional.of(attempt));
        when(interviewSessionRepository.avgOverallScoreOf(MEMBER_ID)).thenReturn(7.46);
        when(applicationRepository.countByCandidate(member)).thenReturn(3L);
        when(interviewSessionRepository.countByCandidate(member)).thenReturn(2L);
        when(interviewSlotRepository.countByBookedByAndCancelledFalse(member)).thenReturn(1L);
        when(candidateProfileRepository.findByUser(member)).thenReturn(Optional.of(CandidateProfile.builder()
                .skills("Java, Spring ,, Java").experienceYears(3).summary("Backend dev")
                .careerGoal("Senior").cvFileName("cv.pdf").updatedAt(loginAt).build()));

        UserDetailResponse detail = service.getUserDetail(MEMBER_ID);

        assertThat(detail.getLastLoginAt()).isEqualTo(attempt.getTimestamp());
        assertThat(detail.getLastIp()).isEqualTo("203.0.113.9");
        assertThat(detail.getAiReadinessScore()).isEqualTo(7.5);
        assertThat(detail.getActivity().getApplications()).isEqualTo(3);
        assertThat(detail.getActivity().getMockSessions()).isEqualTo(2);
        assertThat(detail.getActivity().getBookedInterviews()).isEqualTo(1);
        assertThat(detail.getProfile().getSkills()).containsExactly("Java", "Spring");
        assertThat(detail.getProfile().getCvFileName()).isEqualTo("cv.pdf");

        AdminAuditLog log = captureAudit();
        assertThat(log.getAction()).isEqualTo(AuditAction.VIEW_USER.name());
        assertThat(log.isSucceeded()).isTrue();
        assertThat(log.getTargetUserId()).isEqualTo(MEMBER_ID);
    }

    @Test
    @DisplayName("Chi tiết user: chưa có hồ sơ thì profile = null, chưa có điểm thì aiReadinessScore = null")
    void getUserDetail_leavesMissingBlocksNull() {
        User member = member(MEMBER_ID, MEMBER_EMAIL);
        when(userRepository.findById(MEMBER_ID)).thenReturn(Optional.of(member));
        when(loginAttemptRepository.findTop1ByEmailAndSuccessTrueOrderByTimestampDesc(MEMBER_EMAIL))
                .thenReturn(Optional.empty());
        when(interviewSessionRepository.avgOverallScoreOf(MEMBER_ID)).thenReturn(null);
        when(candidateProfileRepository.findByUser(member)).thenReturn(Optional.empty());

        UserDetailResponse detail = service.getUserDetail(MEMBER_ID);

        assertThat(detail.getLastLoginAt()).isNull();
        assertThat(detail.getAiReadinessScore()).isNull();
        assertThat(detail.getProfile()).isNull();
        assertThat(detail.getActivity().getApplications()).isZero();
    }

    @Test
    @DisplayName("Dòng thời gian gộp 3 nguồn, sắp xếp mới nhất trước và cắt theo trang")
    void getUserActivity_mergesAndSortsThreeSources() {
        User member = member(MEMBER_ID, MEMBER_EMAIL);
        LocalDateTime base = LocalDateTime.of(2026, 11, 10, 9, 0);
        when(userRepository.findById(MEMBER_ID)).thenReturn(Optional.of(member));

        com.aiinterview.platform.model.entity.JobDescription job =
                mock(com.aiinterview.platform.model.entity.JobDescription.class);
        when(job.getTitle()).thenReturn("Backend Engineer");

        Application application = mock(Application.class);
        when(application.getId()).thenReturn(11L);
        when(application.getStatus()).thenReturn(ApplicationStatus.PENDING);
        when(application.getJob()).thenReturn(job);
        when(application.getCreatedAt()).thenReturn(base);

        InterviewSession session = mock(InterviewSession.class);
        when(session.getId()).thenReturn(22L);
        when(session.getTopic()).thenReturn("Spring Boot");
        when(session.getStatus()).thenReturn(InterviewSessionStatus.COMPLETED);
        when(session.getCreatedAt()).thenReturn(base.plusHours(2));

        User employer = User.builder().id(9L).fullName("Acme HR").build();
        InterviewSlot slot = mock(InterviewSlot.class);
        when(slot.getId()).thenReturn(33L);
        when(slot.getEmployer()).thenReturn(employer);
        when(slot.getStartTime()).thenReturn(base.plusHours(1));

        when(applicationRepository.findByCandidateOrderByCreatedAtDesc(eq(member), any()))
                .thenReturn(new PageImpl<>(List.of(application)));
        when(interviewSessionRepository.findByCandidateOrderByCreatedAtDesc(eq(member), any()))
                .thenReturn(new PageImpl<>(List.of(session)));
        when(interviewSlotRepository.findByBookedByAndCancelledFalseOrderByStartTimeDesc(eq(member), any()))
                .thenReturn(new PageImpl<>(List.of(slot)));
        when(applicationRepository.countByCandidate(member)).thenReturn(1L);
        when(interviewSessionRepository.countByCandidate(member)).thenReturn(1L);
        when(interviewSlotRepository.countByBookedByAndCancelledFalse(member)).thenReturn(1L);

        Page<UserActivityResponse> page = service.getUserActivity(MEMBER_ID, 0, 2);

        assertThat(page.getTotalElements()).isEqualTo(3);
        assertThat(page.getContent()).extracting(UserActivityResponse::getType)
                .containsExactly(UserActivityResponse.ActivityType.MOCK_SESSION,
                        UserActivityResponse.ActivityType.INTERVIEW_SLOT);
        assertThat(page.getContent().get(1).getTitle()).isEqualTo("Acme HR");
        assertThat(page.getContent().get(1).getStatus()).isEqualTo("BOOKED");
    }

    @Test
    @DisplayName("Dòng thời gian ghép trong bộ nhớ nên chặn xem quá 1000 dòng đầu")
    void getUserActivity_rejectsDeepPaging() {
        when(userRepository.findById(MEMBER_ID)).thenReturn(Optional.of(member(MEMBER_ID, MEMBER_EMAIL)));

        assertThatThrownBy(() -> service.getUserActivity(MEMBER_ID, 5, 200))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("1000");
    }

    @Test
    @DisplayName("Audit log: điền email/họ tên người bị tác động bằng MỘT lần đọc users")
    void getAuditLogs_fillsTargetProfile() {
        User member = member(MEMBER_ID, MEMBER_EMAIL);
        AdminAuditLog withTarget = AdminAuditLog.builder()
                .id(1L).adminId(ADMIN_ID).adminEmail(ADMIN_EMAIL).targetUserId(MEMBER_ID)
                .action(AuditAction.LOCK_USER.name()).succeeded(true).build();
        AdminAuditLog withoutTarget = AdminAuditLog.builder()
                .id(2L).adminId(ADMIN_ID).adminEmail(ADMIN_EMAIL)
                .action(AuditAction.EXPORT_USERS.name()).succeeded(true).build();
        when(auditLogRepository.search(isNull(), isNull(), isNull(), isNull(), isNull(), any()))
                .thenReturn(new PageImpl<>(List.of(withTarget, withoutTarget)));
        when(userRepository.findAllById(List.of(MEMBER_ID))).thenReturn(List.of(member));

        Page<AuditLogResponse> page = service.getAuditLogs(
                new AuditLogQuery(0, 20, null, null, null, null, null));

        assertThat(page.getContent().get(0).getTargetEmail()).isEqualTo(MEMBER_EMAIL);
        assertThat(page.getContent().get(0).getTargetFullName()).isEqualTo("Member 5");
        assertThat(page.getContent().get(1).getTargetEmail()).isNull();
    }

    // ── khóa / mở khóa ───────────────────────────────────────────────────────

    @Test
    @DisplayName("Khóa: set lockedAt, tăng tokenVersion, thu hồi phiên, ghi audit kèm lý do và gửi mail")
    void lockUser_appliesAllSideEffects() {
        User member = member(MEMBER_ID, MEMBER_EMAIL);
        when(userRepository.findById(MEMBER_ID)).thenReturn(Optional.of(member));
        LockAccountRequest request = new LockAccountRequest();
        request.setReason("  spam tin tuyển dụng  ");

        UserDetailResponse detail = service.lockUser(MEMBER_ID, request);

        assertThat(detail.isLocked()).isTrue();
        assertThat(member.isLocked()).isTrue();
        assertThat(member.getLockedAt()).isNotNull();
        assertThat(member.getTokenVersion()).isEqualTo(1);
        verify(userRepository).save(member);
        verify(userSessionService).revokeAll(MEMBER_ID);
        verify(emailService).sendAccountLockedEmail(MEMBER_EMAIL, "Member 5");

        AdminAuditLog log = captureAudit();
        assertThat(log.getAction()).isEqualTo(AuditAction.LOCK_USER.name());
        assertThat(log.getDetail()).contains("Lý do: spam tin tuyển dụng").doesNotContain("  spam");
    }

    @Test
    @DisplayName("Khóa: notifyUser=false thì không gửi mail nhưng vẫn khóa và ghi audit")
    void lockUser_canSkipNotification() {
        User member = member(MEMBER_ID, MEMBER_EMAIL);
        when(userRepository.findById(MEMBER_ID)).thenReturn(Optional.of(member));
        LockAccountRequest request = new LockAccountRequest();
        request.setNotifyUser(false);

        service.lockUser(MEMBER_ID, request);

        assertThat(member.isLocked()).isTrue();
        verify(emailService, never()).sendAccountLockedEmail(anyString(), anyString());
    }

    @Test
    @DisplayName("Khóa tài khoản đã bị khóa: báo lỗi và ghi audit thất bại (succeeded=false)")
    void lockUser_rejectsAlreadyLockedAndAudits() {
        User member = member(MEMBER_ID, MEMBER_EMAIL);
        member.setLocked(true);
        when(userRepository.findById(MEMBER_ID)).thenReturn(Optional.of(member));

        assertThatThrownBy(() -> service.lockUser(MEMBER_ID, null))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Tài khoản đã bị khóa");

        verify(auditWriter).writeRejected(admin, MEMBER_ID, AuditAction.LOCK_USER, "Tài khoản đã bị khóa");
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("Không được khóa tài khoản quản trị viên khác")
    void lockUser_rejectsAdminTarget() {
        User otherAdmin = User.builder().id(9L).email("admin2@aiinterview.vn")
                .fullName("Admin 2").role(Role.ROLE_ADMIN).enabled(true).build();
        when(userRepository.findById(9L)).thenReturn(Optional.of(otherAdmin));

        assertThatThrownBy(() -> service.lockUser(9L, null))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Không thể khóa tài khoản quản trị viên");
    }

    @Test
    @DisplayName("Không ai được khóa chính mình")
    void lockUser_rejectsSelf() {
        User me = member(ADMIN_ID, ADMIN_EMAIL);
        me.setRole(Role.ROLE_ADMIN);
        when(userRepository.findById(ADMIN_ID)).thenReturn(Optional.of(me));

        assertThatThrownBy(() -> service.lockUser(ADMIN_ID, null))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Không thể khóa tài khoản của chính mình");
    }

    @Test
    @DisplayName("Tài khoản đã bị xóa mềm: 404 với thông điệp riêng, không phải 'không tìm thấy'")
    void lockUser_rejectsDeletedTarget() {
        User deleted = member(MEMBER_ID, MEMBER_EMAIL);
        deleted.setDeletedAt(LocalDateTime.now());
        when(userRepository.findById(MEMBER_ID)).thenReturn(Optional.of(deleted));

        assertThatThrownBy(() -> service.lockUser(MEMBER_ID, null))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Không tìm thấy tài khoản (đã bị xóa)");
    }

    @Test
    @DisplayName("Lý do khóa dài quá 500 ký tự bị chặn trước khi đọc DB")
    void lockUser_rejectsOverlongReason() {
        LockAccountRequest request = new LockAccountRequest();
        request.setReason("x".repeat(501));

        assertThatThrownBy(() -> service.lockUser(MEMBER_ID, request))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Lý do tối đa 500 ký tự");
        verifyNoInteractions(userRepository);
    }

    @Test
    @DisplayName("Mở khóa: xóa lockedAt và gửi mail; tài khoản không bị khóa thì báo lỗi")
    void unlockUser_clearsLockOrRejects() {
        User locked = member(MEMBER_ID, MEMBER_EMAIL);
        locked.setLocked(true);
        locked.setLockedAt(LocalDateTime.now());
        when(userRepository.findById(MEMBER_ID)).thenReturn(Optional.of(locked));

        UserDetailResponse detail = service.unlockUser(MEMBER_ID);

        assertThat(detail.isLocked()).isFalse();
        assertThat(locked.getLockedAt()).isNull();
        verify(emailService).sendAccountUnlockedEmail(MEMBER_EMAIL, "Member 5");

        User active = member(6L, "active@example.com");
        when(userRepository.findById(6L)).thenReturn(Optional.of(active));
        assertThatThrownBy(() -> service.unlockUser(6L))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Tài khoản không ở trạng thái bị khóa");
    }

    // ── bật / tắt / xóa / khôi phục ──────────────────────────────────────────

    @Test
    @DisplayName("Tắt tài khoản: tăng tokenVersion + thu hồi phiên để JWT cũ chết ngay")
    void setUserEnabled_disableKillsSessions() {
        User member = member(MEMBER_ID, MEMBER_EMAIL);
        member.setTokenVersion(3);
        when(userRepository.findById(MEMBER_ID)).thenReturn(Optional.of(member));

        service.setUserEnabled(MEMBER_ID, false);

        assertThat(member.isEnabled()).isFalse();
        assertThat(member.getTokenVersion()).isEqualTo(4);
        verify(userSessionService).revokeAll(MEMBER_ID);
        assertThat(captureAudit().getAction()).isEqualTo(AuditAction.DISABLE_USER.name());
    }

    @Test
    @DisplayName("Bật tài khoản còn chờ xác thực email: chặn, vì user chưa chứng minh sở hữu hộp thư")
    void setUserEnabled_rejectsPendingVerification() {
        User pending = User.builder().id(MEMBER_ID).email(MEMBER_EMAIL).fullName("Member 5")
                .role(Role.ROLE_USER).enabled(false).verificationToken("tok").build();
        when(userRepository.findById(MEMBER_ID)).thenReturn(Optional.of(pending));

        assertThatThrownBy(() -> service.setUserEnabled(MEMBER_ID, true))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("chưa xác thực email");
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("Bật/tắt lặp lại trạng thái hiện tại là lỗi 400, không phải no-op im lặng")
    void setUserEnabled_rejectsNoOp() {
        User member = member(MEMBER_ID, MEMBER_EMAIL);
        when(userRepository.findById(MEMBER_ID)).thenReturn(Optional.of(member));

        assertThatThrownBy(() -> service.setUserEnabled(MEMBER_ID, true))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Tài khoản đã ở trạng thái hoạt động");
    }

    @Test
    @DisplayName("Xóa mềm: set deletedAt, thu hồi phiên và gửi mail; tài khoản admin cuối cùng không được xóa")
    void deleteUser_softDeletesOrRefusesLastAdmin() {
        User member = member(MEMBER_ID, MEMBER_EMAIL);
        when(userRepository.findById(MEMBER_ID)).thenReturn(Optional.of(member));

        service.deleteUser(MEMBER_ID);

        assertThat(member.getDeletedAt()).isNotNull();
        assertThat(member.getTokenVersion()).isEqualTo(1);
        verify(userSessionService).revokeAll(MEMBER_ID);
        verify(emailService).sendAccountDeletedEmail(MEMBER_EMAIL, "Member 5");

        User lastAdmin = User.builder().id(9L).email("admin2@aiinterview.vn")
                .fullName("Admin 2").role(Role.ROLE_ADMIN).enabled(true).build();
        when(userRepository.findById(9L)).thenReturn(Optional.of(lastAdmin));
        when(userRepository.countByRoleAndDeletedAtIsNull(Role.ROLE_ADMIN)).thenReturn(1L);

        assertThatThrownBy(() -> service.deleteUser(9L))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("ít nhất một tài khoản Quản trị viên");
    }

    @Test
    @DisplayName("Không ai được xóa chính mình")
    void deleteUser_rejectsSelf() {
        User me = member(ADMIN_ID, ADMIN_EMAIL);
        me.setRole(Role.ROLE_ADMIN);
        when(userRepository.findById(ADMIN_ID)).thenReturn(Optional.of(me));

        assertThatThrownBy(() -> service.deleteUser(ADMIN_ID))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Không thể xóa tài khoản của chính mình");
    }

    @Test
    @DisplayName("Khôi phục: chỉ xóa deletedAt, giữ nguyên role/enabled/locked trước khi xóa")
    void restoreUser_clearsDeletedAtOnly() {
        User deleted = member(MEMBER_ID, MEMBER_EMAIL);
        deleted.setDeletedAt(LocalDateTime.now().minusDays(2));
        deleted.setLocked(true);
        deleted.setTokenVersion(4);
        when(userRepository.findById(MEMBER_ID)).thenReturn(Optional.of(deleted));

        UserDetailResponse detail = service.restoreUser(MEMBER_ID);

        assertThat(detail.getDeletedAt()).isNull();
        assertThat(deleted.isLocked()).isTrue();
        assertThat(deleted.getTokenVersion()).isEqualTo(4);
        assertThat(captureAudit().getAction()).isEqualTo(AuditAction.RESTORE_USER.name());
    }

    @Test
    @DisplayName("Khôi phục tài khoản đang sống bình thường là lỗi 400")
    void restoreUser_rejectsLiveAccount() {
        when(userRepository.findById(MEMBER_ID)).thenReturn(Optional.of(member(MEMBER_ID, MEMBER_EMAIL)));

        assertThatThrownBy(() -> service.restoreUser(MEMBER_ID))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Tài khoản không ở trạng thái đã xóa");
    }

    // ── mật khẩu, vai trò, 2FA ───────────────────────────────────────────────

    @Test
    @DisplayName("Reset mật khẩu: phát token hạn 30 phút và gửi đúng token đó qua mail")
    void resetPassword_issuesThirtyMinuteToken() {
        User member = member(MEMBER_ID, MEMBER_EMAIL);
        when(userRepository.findById(MEMBER_ID)).thenReturn(Optional.of(member));

        LocalDateTime before = LocalDateTime.now();
        service.resetPassword(MEMBER_ID);

        assertThat(member.getResetToken()).isNotBlank();
        assertThat(member.getResetTokenExpiry()).isAfter(before.plusMinutes(29));
        assertThat(member.getResetTokenExpiry()).isBefore(LocalDateTime.now().plusMinutes(31));
        verify(emailService).sendResetPasswordEmail(MEMBER_EMAIL, "Member 5", member.getResetToken());
        assertThat(captureAudit().getAction()).isEqualTo(AuditAction.RESET_PASSWORD.name());
    }

    @Test
    @DisplayName("Đổi vai trò: tăng tokenVersion + thu hồi phiên, audit ghi rõ role cũ → mới")
    void changeRole_bumpsVersionAndAuditsTransition() {
        User member = member(MEMBER_ID, MEMBER_EMAIL);
        when(userRepository.findById(MEMBER_ID)).thenReturn(Optional.of(member));

        service.changeRole(MEMBER_ID, Role.ROLE_INTERVIEWER);

        assertThat(member.getRole()).isEqualTo(Role.ROLE_INTERVIEWER);
        assertThat(member.getTokenVersion()).isEqualTo(1);
        verify(userSessionService).revokeAll(MEMBER_ID);
        assertThat(captureAudit().getDetail()).isEqualTo("Đổi role: ROLE_USER → ROLE_INTERVIEWER | user: " + MEMBER_EMAIL);
    }

    @Test
    @DisplayName("Hạ quyền admin cuối cùng bị chặn để console không bao giờ mất người quản trị")
    void changeRole_refusesDemotingLastAdmin() {
        User lastAdmin = User.builder().id(9L).email("admin2@aiinterview.vn")
                .fullName("Admin 2").role(Role.ROLE_ADMIN).enabled(true).build();
        when(userRepository.findById(9L)).thenReturn(Optional.of(lastAdmin));
        when(userRepository.countByRoleAndDeletedAtIsNull(Role.ROLE_ADMIN)).thenReturn(1L);

        assertThatThrownBy(() -> service.changeRole(9L, Role.ROLE_STAFF))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("thay đổi vai trò");
        assertThat(lastAdmin.getRole()).isEqualTo(Role.ROLE_ADMIN);
    }

    @Test
    @DisplayName("Tắt 2FA bắt buộc: xóa secret và toàn bộ recovery code còn lại")
    void disableUserTwoFactor_clearsSecretAndRecoveryCodes() {
        User member = member(MEMBER_ID, MEMBER_EMAIL);
        member.setTotpEnabled(true);
        member.setTotpSecret("JBSWY3DPEHPK3PXP");
        when(userRepository.findById(MEMBER_ID)).thenReturn(Optional.of(member));

        service.disableUserTwoFactor(MEMBER_ID, "mất điện thoại");

        assertThat(member.isTotpEnabled()).isFalse();
        assertThat(member.getTotpSecret()).isNull();
        verify(recoveryCodeRepository).deleteByUserId(MEMBER_ID);
        assertThat(captureAudit().getDetail()).contains("mất điện thoại");
    }

    @Test
    @DisplayName("Tắt 2FA khi tài khoản chưa bật là lỗi 400")
    void disableUserTwoFactor_rejectsWhenNotEnabled() {
        when(userRepository.findById(MEMBER_ID)).thenReturn(Optional.of(member(MEMBER_ID, MEMBER_EMAIL)));

        assertThatThrownBy(() -> service.disableUserTwoFactor(MEMBER_ID, "lý do"))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Tài khoản chưa bật 2FA");
        verify(recoveryCodeRepository, never()).deleteByUserId(any());
    }

    // ── lời mời ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Mời user: tạo tài khoản disabled với mật khẩu random không ai biết + link hạn 24 giờ")
    void inviteUser_createsDisabledAccountWithInvitationToken() {
        when(userRepository.existsByEmail("new@example.com")).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("ENCODED-RANDOM");
        when(userRepository.save(any(User.class))).thenAnswer(call -> {
            User saved = call.getArgument(0);
            saved.setId(77L);
            return saved;
        });
        AdminInviteUserRequest request = inviteRequest("new@example.com", "Nguyễn Văn A", Role.ROLE_STAFF);

        UserDetailResponse detail = service.inviteUser(request);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        User invited = captor.getValue();
        assertThat(invited.getPassword()).isEqualTo("ENCODED-RANDOM");
        assertThat(invited.isEnabled()).isFalse();
        assertThat(invited.getVerificationToken()).isNotBlank();
        assertThat(invited.getVerificationTokenExpiry())
                .isAfter(LocalDateTime.now().plusHours(23))
                .isBefore(LocalDateTime.now().plusHours(25));
        verify(userRepository).flush();
        verify(emailService).sendVerificationEmail("new@example.com", "Nguyễn Văn A",
                invited.getVerificationToken());
        assertThat(detail.getId()).isEqualTo(77L);
        assertThat(captureAudit().getAction()).isEqualTo(AuditAction.INVITE_USER.name());
    }

    @Test
    @DisplayName("Mời trùng email đang sống: 409 với thông điệp chung")
    void inviteUser_rejectsLiveDuplicate() {
        when(userRepository.existsByEmail(MEMBER_EMAIL)).thenReturn(true);
        when(userRepository.findByEmail(MEMBER_EMAIL)).thenReturn(Optional.of(member(MEMBER_ID, MEMBER_EMAIL)));

        assertThatThrownBy(() -> service.inviteUser(inviteRequest(MEMBER_EMAIL, "Member", Role.ROLE_USER)))
                .isInstanceOf(EmailAlreadyRegisteredException.class)
                .hasMessage("Email đã tồn tại trong hệ thống");
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("Mời trùng email của tài khoản đã xóa: chỉ luôn lối khôi phục thay vì tạo mới")
    void inviteUser_pointsToRestoreForDeletedEmail() {
        User deleted = member(42L, MEMBER_EMAIL);
        deleted.setDeletedAt(LocalDateTime.now());
        when(userRepository.existsByEmail(MEMBER_EMAIL)).thenReturn(true);
        when(userRepository.findByEmail(MEMBER_EMAIL)).thenReturn(Optional.of(deleted));

        assertThatThrownBy(() -> service.inviteUser(inviteRequest(MEMBER_EMAIL, "Member", Role.ROLE_USER)))
                .isInstanceOf(EmailAlreadyRegisteredException.class)
                .hasMessageContaining("42")
                .hasMessageContaining("/restore");
    }

    @Test
    @DisplayName("Email vừa bị request khác chiếm: unique constraint trong lúc flush cũng thành 409, không phải 500")
    void inviteUser_translatesUniqueViolation() {
        when(userRepository.existsByEmail("new@example.com")).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("ENCODED");
        when(userRepository.save(any(User.class))).thenThrow(new DataIntegrityViolationException("dup"));
        when(userRepository.findByEmail("new@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.inviteUser(inviteRequest("new@example.com", "A", Role.ROLE_USER)))
                .isInstanceOf(EmailAlreadyRegisteredException.class);
    }

    @Test
    @DisplayName("Gửi lại lời mời: xoay token mới; tài khoản đã xác thực thì không gửi được lời mời nữa")
    void resendInvitation_rotatesTokenOrRejectsVerified() {
        User pending = User.builder().id(MEMBER_ID).email(MEMBER_EMAIL).fullName("Member 5")
                .role(Role.ROLE_USER).enabled(false).verificationToken("old-token").build();
        when(userRepository.findById(MEMBER_ID)).thenReturn(Optional.of(pending));

        service.resendInvitation(MEMBER_ID);

        assertThat(pending.getVerificationToken()).isNotBlank().isNotEqualTo("old-token");
        verify(emailService).sendResendVerificationEmail(MEMBER_EMAIL, "Member 5", pending.getVerificationToken());

        User verified = member(6L, "verified@example.com");
        when(userRepository.findById(6L)).thenReturn(Optional.of(verified));
        assertThatThrownBy(() -> service.resendInvitation(6L))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Tài khoản đã xác thực email");
    }

    @Test
    @DisplayName("Thu hồi lời mời: chỉ link chết, không khóa tài khoản — vẫn gửi lại lời mời được")
    void revokeInvitation_killsLinkOnly() {
        User pending = User.builder().id(MEMBER_ID).email(MEMBER_EMAIL).fullName("Member 5")
                .role(Role.ROLE_USER).enabled(false).verificationToken("tok")
                .verificationTokenExpiry(LocalDateTime.now().plusHours(5)).build();
        when(userRepository.findById(MEMBER_ID)).thenReturn(Optional.of(pending));

        service.revokeInvitation(MEMBER_ID);

        assertThat(pending.getVerificationToken()).isNull();
        assertThat(pending.getVerificationTokenExpiry()).isNull();
        assertThat(pending.isLocked()).isFalse();
        assertThat(pending.getTokenVersion()).isZero();

        when(userRepository.findById(6L)).thenReturn(Optional.of(member(6L, "verified@example.com")));
        assertThatThrownBy(() -> service.revokeInvitation(6L))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Tài khoản không có lời mời đang chờ xác thực");
    }

    // ── sửa hồ sơ ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Sửa hồ sơ chỉ đổi tên: không đụng email, không thu hồi phiên")
    void updateProfile_renamesWithoutTouchingEmail() {
        User member = member(MEMBER_ID, MEMBER_EMAIL);
        when(userRepository.findById(MEMBER_ID)).thenReturn(Optional.of(member));
        AdminUpdateProfileRequest request = new AdminUpdateProfileRequest();
        request.setFullName("  Trần Thị B  ");

        AdminProfileUpdateResponse response = service.updateProfile(MEMBER_ID, request);

        assertThat(member.getFullName()).isEqualTo("Trần Thị B");
        assertThat(member.getEmail()).isEqualTo(MEMBER_EMAIL);
        assertThat(response.emailChanged()).isFalse();
        verify(userSessionService, never()).revokeAll(any());
        verify(emailService, never()).sendVerificationEmail(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("Đổi email: về trạng thái chờ xác thực, phát token mới, thu hồi phiên và gửi link tới địa chỉ mới")
    void updateProfile_emailChangeRestartsVerification() {
        User member = member(MEMBER_ID, MEMBER_EMAIL);
        member.setTokenVersion(2);
        when(userRepository.findById(MEMBER_ID)).thenReturn(Optional.of(member));
        when(userRepository.existsByEmail("new@example.com")).thenReturn(false);
        AdminUpdateProfileRequest request = new AdminUpdateProfileRequest();
        request.setEmail("new@example.com");

        AdminProfileUpdateResponse response = service.updateProfile(MEMBER_ID, request);

        assertThat(member.getEmail()).isEqualTo("new@example.com");
        assertThat(member.isEnabled()).isFalse();
        assertThat(member.getVerificationToken()).isNotBlank();
        assertThat(member.getTokenVersion()).isEqualTo(3);
        assertThat(response.emailChanged()).isTrue();
        verify(userSessionService).revokeAll(MEMBER_ID);
        verify(emailService).sendVerificationEmail("new@example.com", "Member 5", member.getVerificationToken());
    }

    @Test
    @DisplayName("Admin không được tự đổi email của chính mình (sẽ tự khóa phiên đang dùng)")
    void updateProfile_refusesOwnEmailChange() {
        User me = member(ADMIN_ID, ADMIN_EMAIL);
        me.setRole(Role.ROLE_ADMIN);
        when(userRepository.findById(ADMIN_ID)).thenReturn(Optional.of(me));
        AdminUpdateProfileRequest request = new AdminUpdateProfileRequest();
        request.setEmail("other@aiinterview.vn");

        assertThatThrownBy(() -> service.updateProfile(ADMIN_ID, request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("chính tài khoản quản trị");
    }

    @Test
    @DisplayName("Sửa hồ sơ: tên toàn khoảng trắng và request rỗng đều là 400")
    void updateProfile_rejectsBlankNameAndEmptyRequest() {
        when(userRepository.findById(MEMBER_ID)).thenReturn(Optional.of(member(MEMBER_ID, MEMBER_EMAIL)));
        AdminUpdateProfileRequest blankName = new AdminUpdateProfileRequest();
        blankName.setFullName("   ");

        assertThatThrownBy(() -> service.updateProfile(MEMBER_ID, blankName))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Họ tên không được để trống");

        assertThatThrownBy(() -> service.updateProfile(MEMBER_ID, new AdminUpdateProfileRequest()))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Không có thông tin nào để cập nhật");
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("Đổi sang email đã có người dùng là 409")
    void updateProfile_rejectsTakenEmail() {
        when(userRepository.findById(MEMBER_ID)).thenReturn(Optional.of(member(MEMBER_ID, MEMBER_EMAIL)));
        when(userRepository.existsByEmail("taken@example.com")).thenReturn(true);
        when(userRepository.findByEmail("taken@example.com"))
                .thenReturn(Optional.of(member(9L, "taken@example.com")));
        AdminUpdateProfileRequest request = new AdminUpdateProfileRequest();
        request.setEmail("taken@example.com");

        assertThatThrownBy(() -> service.updateProfile(MEMBER_ID, request))
                .isInstanceOf(EmailAlreadyRegisteredException.class);
    }

    // ── export CSV ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("CSV: có header, ô trống thay cho 'null' và chặn formula injection/quote đúng chuẩn")
    void exportUsers_writesSafeCsv() {
        User risky = User.builder()
                .id(MEMBER_ID).email(MEMBER_EMAIL).fullName("=cmd, \"quoted\"")
                .role(Role.ROLE_USER).enabled(true).build();
        when(userRepository.findAllWithFilters(eq(null), eq(null), eq(null), any()))
                .thenReturn(new PageImpl<>(List.of(risky)));

        String csv = service.exportUsers(null, null, null);

        assertThat(csv).startsWith("id,email,fullName,role,status,pendingVerification,"
                + "lastLoginAt,createdAt,deletedAt\n");
        // '=' bị vô hiệu bằng dấu nháy đơn, dấu phẩy + ngoặc kép được bọc/escape theo RFC 4180.
        assertThat(csv).contains("5,member@example.com,\"'=cmd, \"\"quoted\"\"\",ROLE_USER,ACTIVE,false,,,\n");
        assertThat(csv).doesNotContain("null");
        assertThat(captureAudit().getAction()).isEqualTo(AuditAction.EXPORT_USERS.name());
    }

    @Test
    @DisplayName("CSV: quá 10.000 dòng thì từ chối thay vì treo request")
    void exportUsers_refusesTooManyRows() {
        when(userRepository.findAllWithFilters(eq(null), eq(null), eq(null), any()))
                .thenReturn(new PageImpl<>(List.of(), Pageable.unpaged(), 10_001));

        assertThatThrownBy(() -> service.exportUsers(null, null, null))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("10000");
    }

    @Test
    @DisplayName("CSV: filter DELETED có endpoint riêng nên bị từ chối ở đây")
    void exportUsers_rejectsDeletedFilter() {
        assertThatThrownBy(() -> service.exportUsers(null, UserStatusFilter.DELETED, null))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("/api/v1/admin/users/deleted");
    }

    // ── bulk ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Bulk khóa: id lỗi được báo riêng, các id còn lại vẫn được xử lý")
    void bulkLock_collectsPerIdFailures() {
        User member = member(MEMBER_ID, MEMBER_EMAIL);
        when(userRepository.findById(MEMBER_ID)).thenReturn(Optional.of(member));
        when(userRepository.findById(404L)).thenReturn(Optional.empty());
        BulkUserActionRequest request = new BulkUserActionRequest();
        request.setIds(List.of(MEMBER_ID, 404L));

        BulkUserActionResult result = service.bulkLock(request);

        assertThat(result.requested()).isEqualTo(2);
        assertThat(result.succeeded()).isEqualTo(1);
        assertThat(result.failed()).hasSize(1);
        assertThat(result.failed().get(0).id()).isEqualTo(404L);
        assertThat(member.isLocked()).isTrue();
    }

    @Test
    @DisplayName("Bulk đổi vai trò dùng chung đường xử lý: 1 id thành công, 1 id không tồn tại")
    void bulkRole_reportsPartialSuccess() {
        User member = member(MEMBER_ID, MEMBER_EMAIL);
        when(userRepository.findById(MEMBER_ID)).thenReturn(Optional.of(member));
        when(userRepository.findById(404L)).thenReturn(Optional.empty());
        BulkRoleChangeRequest request = new BulkRoleChangeRequest();
        request.setIds(List.of(MEMBER_ID, 404L));
        request.setRole(Role.ROLE_INTERVIEWER);

        BulkUserActionResult result = service.bulkRole(request);

        assertThat(result.succeeded()).isEqualTo(1);
        assertThat(result.failed()).hasSize(1);
        assertThat(member.getRole()).isEqualTo(Role.ROLE_INTERVIEWER);
        verify(userSessionService).revokeAll(MEMBER_ID);
    }

    // ── xác thực admin ───────────────────────────────────────────────────────

    @Test
    @DisplayName("Không có phiên đăng nhập: từ chối thay vì ghi audit với admin giả")
    void requireAdmin_rejectsAnonymous() {
        SecurityContextHolder.clearContext();

        assertThatThrownBy(() -> service.getUserDetail(MEMBER_ID))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("Không xác định được tài khoản quản trị");
        verifyNoInteractions(userRepository);
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private void loginAs(User user) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                user, null, List.of(new SimpleGrantedAuthority(user.getRole().name()))));
    }

    private static User member(long id, String email) {
        return User.builder()
                .id(id)
                .email(email)
                .fullName("Member " + id)
                .role(Role.ROLE_USER)
                .enabled(true)
                .build();
    }

    private static AdminInviteUserRequest inviteRequest(String email, String fullName, Role role) {
        AdminInviteUserRequest request = new AdminInviteUserRequest();
        request.setEmail(email);
        request.setFullName(fullName);
        request.setRole(role);
        return request;
    }

    private AdminAuditLog captureAudit() {
        ArgumentCaptor<AdminAuditLog> captor = ArgumentCaptor.forClass(AdminAuditLog.class);
        verify(auditLogRepository, times(1)).save(captor.capture());
        return captor.getValue();
    }
}
