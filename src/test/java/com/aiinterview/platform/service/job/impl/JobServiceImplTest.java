package com.aiinterview.platform.service.job.impl;

import com.aiinterview.platform.common.exception.BadRequestException;
import com.aiinterview.platform.common.exception.ResourceNotFoundException;
import com.aiinterview.platform.model.dto.request.JobRequest;
import com.aiinterview.platform.model.dto.request.JobReviewRequest;
import com.aiinterview.platform.model.dto.response.JobDetailResponse;
import com.aiinterview.platform.model.entity.AdminAuditLog;
import com.aiinterview.platform.model.entity.JobDescription;
import com.aiinterview.platform.model.entity.JobModerationRecord;
import com.aiinterview.platform.model.entity.User;
import com.aiinterview.platform.model.enums.AiScreeningTrack;
import com.aiinterview.platform.model.enums.JobStatus;
import com.aiinterview.platform.model.enums.ModerationAction;
import com.aiinterview.platform.model.enums.ModerationActorRole;
import com.aiinterview.platform.model.enums.Role;
import com.aiinterview.platform.model.repository.AdminAuditLogRepository;
import com.aiinterview.platform.model.repository.JobDescriptionRepository;
import com.aiinterview.platform.model.repository.JobModerationRecordRepository;
import com.aiinterview.platform.model.repository.UserRepository;
import com.aiinterview.platform.service.job.MatchingService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Business rules of the job lifecycle (spec 003/004/009): DRAFT -> PENDING_APPROVAL -> OPEN,
 * the reject path back to DRAFT, and the wizard payload guards. Repositories are mocked, so a
 * regression is reported here in milliseconds instead of through the Postman flow.
 */
class JobServiceImplTest {

    private static final Long JOB_ID = 7L;

    private JobServiceImpl service;
    private JobDescriptionRepository jobRepository;
    private UserRepository userRepository;
    private MatchingService matchingService;
    private AdminAuditLogRepository auditLogRepository;
    private JobModerationRecordRepository moderationRecordRepository;

    private User employer;
    private User stranger;
    private User admin;

    @BeforeEach
    void setUp() {
        jobRepository = mock(JobDescriptionRepository.class);
        userRepository = mock(UserRepository.class);
        matchingService = mock(MatchingService.class);
        auditLogRepository = mock(AdminAuditLogRepository.class);
        moderationRecordRepository = mock(JobModerationRecordRepository.class);
        service = new JobServiceImpl(jobRepository, userRepository, matchingService,
                auditLogRepository, moderationRecordRepository);

        employer = user(10L, "employer@example.com", "Employer User", Role.ROLE_INTERVIEWER);
        stranger = user(11L, "stranger@example.com", "Stranger User", Role.ROLE_INTERVIEWER);
        admin = user(1L, "admin@example.com", "Admin User", Role.ROLE_ADMIN);

        when(userRepository.findByEmail(employer.getEmail())).thenReturn(Optional.of(employer));
        when(userRepository.findByEmail(stranger.getEmail())).thenReturn(Optional.of(stranger));
        when(userRepository.findByEmail(admin.getEmail())).thenReturn(Optional.of(admin));
        when(jobRepository.save(any(JobDescription.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("POST /jobs luon tao ban nhap DRAFT thuoc ve nguoi dang dang nhap")
    void createJob_alwaysPersistsDraftForCurrentEmployer() {
        signIn(employer);
        JobRequest request = request();

        JobDetailResponse response = service.createJob(request);

        assertThat(response.getStatus()).isEqualTo(JobStatus.DRAFT);
        assertThat(response.getEmployerId()).isEqualTo(employer.getId());
        assertThat(response.getTitle()).isEqualTo("Senior Backend Engineer");
        verify(jobRepository).save(any(JobDescription.class));
    }

    @Test
    @DisplayName("PUT /jobs/{id} khi tin dang cho duyet bi chan 400")
    void updateJob_whilePendingApproval_returnsBadRequest() {
        signIn(employer);
        givenJob(JobStatus.PENDING_APPROVAL);

        assertThatThrownBy(() -> service.updateJob(JOB_ID, request()))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Tin đang chờ duyệt, không thể sửa");
        verify(jobRepository, never()).save(any(JobDescription.class));
    }

    @Test
    @DisplayName("PUT /jobs/{id} boi nguoi khong so huu bi chan 400")
    void updateJob_byNonOwner_returnsBadRequest() {
        signIn(stranger);
        givenJob(JobStatus.DRAFT);

        assertThatThrownBy(() -> service.updateJob(JOB_ID, request()))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Bạn không có quyền thao tác trên tin tuyển dụng này");
        verify(jobRepository, never()).save(any(JobDescription.class));
    }

    @Test
    @DisplayName("PATCH /jobs/{id}/submit ghi PENDING_APPROVAL, moc submit va dong SUBMITTED")
    void submitForReview_fromDraft_movesToPendingAndRecordsSubmission() {
        signIn(employer);
        JobDescription job = givenJob(JobStatus.DRAFT);
        when(moderationRecordRepository.existsByJobIdAndAction(JOB_ID, ModerationAction.REJECTED))
                .thenReturn(false);

        JobDetailResponse response = service.submitForReview(JOB_ID);

        assertThat(response.getStatus()).isEqualTo(JobStatus.PENDING_APPROVAL);
        assertThat(job.getSubmittedAt()).isNotNull();
        // 009 FR-006: cot ai_safety_score duoc ghi lan dau ngay luc nop
        assertThat(job.getAiSafetyScore()).isNotNull();
        assertThat(capturedModerationRecord().getAction()).isEqualTo(ModerationAction.SUBMITTED);
        assertThat(capturedModerationRecord().getActorRole()).isEqualTo(ModerationActorRole.EMPLOYER);
    }

    @Test
    @DisplayName("Nop lai tin tung bi tu choi ghi dong RESUBMITTED thay vi SUBMITTED")
    void submitForReview_afterRejection_recordsResubmission() {
        signIn(employer);
        givenJob(JobStatus.DRAFT);
        when(moderationRecordRepository.existsByJobIdAndAction(JOB_ID, ModerationAction.REJECTED))
                .thenReturn(true);

        service.submitForReview(JOB_ID);

        assertThat(capturedModerationRecord().getAction()).isEqualTo(ModerationAction.RESUBMITTED);
    }

    @Test
    @DisplayName("PATCH /jobs/{id}/submit chi chay tu DRAFT")
    void submitForReview_fromOpen_returnsBadRequest() {
        signIn(employer);
        givenJob(JobStatus.OPEN);

        assertThatThrownBy(() -> service.submitForReview(JOB_ID))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Chỉ bản nháp mới gửi duyệt được");
        verifyNoInteractions(moderationRecordRepository);
    }

    @Test
    @DisplayName("PATCH /jobs/{id}/withdraw tra ve DRAFT, xoa moc submit va ghi WITHDRAWN")
    void withdrawFromReview_returnsDraftAndClearsSubmittedAt() {
        signIn(employer);
        JobDescription job = givenJob(JobStatus.PENDING_APPROVAL);
        job.setSubmittedAt(LocalDateTime.now().minusHours(2));

        JobDetailResponse response = service.withdrawFromReview(JOB_ID);

        assertThat(response.getStatus()).isEqualTo(JobStatus.DRAFT);
        assertThat(job.getSubmittedAt()).isNull();
        assertThat(capturedModerationRecord().getAction()).isEqualTo(ModerationAction.WITHDRAWN);
    }

    @Test
    @DisplayName("Duyet tin: OPEN, xoa reviewNote, ghi audit APPROVE_JOB voi admin that")
    void reviewJob_approve_opensJobAndWritesAudit() {
        signIn(admin);
        JobDescription job = givenJob(JobStatus.PENDING_APPROVAL);
        job.setReviewNote("Ly do cu");
        job.setSubmittedAt(LocalDateTime.now().minusMinutes(30));

        JobDetailResponse response = service.reviewJob(JOB_ID, review(true, null));

        assertThat(response.getStatus()).isEqualTo(JobStatus.OPEN);
        assertThat(job.getReviewNote()).isNull();
        assertThat(capturedModerationRecord().getAction()).isEqualTo(ModerationAction.APPROVED);
        assertThat(capturedModerationRecord().getActorRole()).isEqualTo(ModerationActorRole.ADMIN);
        assertThat(capturedModerationRecord().getProcessingSeconds()).isGreaterThanOrEqualTo(0L);

        ArgumentCaptor<AdminAuditLog> audit = ArgumentCaptor.forClass(AdminAuditLog.class);
        verify(auditLogRepository).save(audit.capture());
        assertThat(audit.getValue().getAction()).isEqualTo("APPROVE_JOB");
        assertThat(audit.getValue().getAdminId()).isEqualTo(admin.getId());
        assertThat(audit.getValue().getAdminEmail()).isEqualTo(admin.getEmail());
        assertThat(audit.getValue().getDetail()).contains("job #" + JOB_ID);
    }

    @Test
    @DisplayName("Tu choi khong kem ly do bi chan 400")
    void reviewJob_rejectWithoutReason_returnsBadRequest() {
        signIn(admin);
        givenJob(JobStatus.PENDING_APPROVAL);

        assertThatThrownBy(() -> service.reviewJob(JOB_ID, review(false, "   ")))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Vui lòng nêu lý do từ chối");
        verifyNoInteractions(moderationRecordRepository, auditLogRepository);
    }

    @Test
    @DisplayName("Tu choi co ly do: ve DRAFT, luu note da trim, xoa moc submit")
    void reviewJob_reject_returnsDraftWithTrimmedNote() {
        signIn(admin);
        JobDescription job = givenJob(JobStatus.PENDING_APPROVAL);
        job.setSubmittedAt(LocalDateTime.now().minusMinutes(10));

        JobDetailResponse response = service.reviewJob(JOB_ID, review(false, "  Thiếu mô tả  "));

        assertThat(response.getStatus()).isEqualTo(JobStatus.DRAFT);
        assertThat(job.getReviewNote()).isEqualTo("Thiếu mô tả");
        assertThat(job.getSubmittedAt()).isNull();
        assertThat(capturedModerationRecord().getAction()).isEqualTo(ModerationAction.REJECTED);
        assertThat(capturedModerationRecord().getNote()).isEqualTo("Thiếu mô tả");
    }

    @Test
    @DisplayName("Duyet tin khong o trang thai cho duyet bi chan 400")
    void reviewJob_wrongInitialState_returnsBadRequest() {
        signIn(admin);
        givenJob(JobStatus.DRAFT);

        assertThatThrownBy(() -> service.reviewJob(JOB_ID, review(true, null)))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Tin không ở trạng thái chờ duyệt");
        verifyNoInteractions(auditLogRepository);
    }

    @Test
    @DisplayName("PATCH /jobs/{id}/status chi doi duoc cap OPEN <-> CLOSED")
    void changeStatus_onlyAcceptsOpenClosedPair() {
        signIn(employer);
        JobDescription job = givenJob(JobStatus.OPEN);

        assertThatThrownBy(() -> service.changeStatus(JOB_ID, JobStatus.DRAFT))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Trạng thái không hợp lệ cho tin tuyển dụng này");

        JobDetailResponse closed = service.changeStatus(JOB_ID, JobStatus.CLOSED);
        assertThat(closed.getStatus()).isEqualTo(JobStatus.CLOSED);
        assertThat(job.getStatus()).isEqualTo(JobStatus.CLOSED);
    }

    @Test
    @DisplayName("changeStatus voi status null bi chan truoc khi doc job")
    void changeStatus_nullStatus_returnsBadRequest() {
        assertThatThrownBy(() -> service.changeStatus(JOB_ID, null))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Trạng thái không hợp lệ");
        verifyNoInteractions(jobRepository);
    }

    @Test
    @DisplayName("Wizard: salaryMin > salaryMax bi chan khi tao tin")
    void createJob_salaryMinGreaterThanMax_returnsBadRequest() {
        signIn(employer);
        JobRequest request = request();
        request.setSalaryMin(300_000L);
        request.setSalaryMax(210_000L);

        assertThatThrownBy(() -> service.createJob(request))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Mức lương tối thiểu không được lớn hơn mức lương tối đa");
        verify(jobRepository, never()).save(any(JobDescription.class));
    }

    @Test
    @DisplayName("Wizard: bat AI screening ma thieu track hoac thieu nguong diem bi chan")
    void createJob_aiScreeningWithoutTrackOrScore_returnsBadRequest() {
        signIn(employer);

        JobRequest missingTrack = request();
        missingTrack.setAiScreeningEnabled(true);
        assertThatThrownBy(() -> service.createJob(missingTrack))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Vui lòng chọn hình thức phỏng vấn AI khi bật sàng lọc tự động");

        JobRequest missingScore = request();
        missingScore.setAiScreeningEnabled(true);
        missingScore.setAiScreeningTrack(AiScreeningTrack.LIVE_CODING);
        assertThatThrownBy(() -> service.createJob(missingScore))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Vui lòng đặt ngưỡng điểm đậu khi bật sàng lọc tự động");

        verify(jobRepository, never()).save(any(JobDescription.class));
    }

    @Test
    @DisplayName("GET /jobs/{id}: ban nhap bi an voi nguoi la, chu tin van doc duoc")
    void getJobDetail_hidesDraftFromStrangerButNotFromOwner() {
        givenJob(JobStatus.DRAFT);

        signIn(stranger);
        assertThatThrownBy(() -> service.getJobDetail(JOB_ID))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Không tìm thấy tin tuyển dụng với id: " + JOB_ID);

        signIn(employer);
        assertThat(service.getJobDetail(JOB_ID).getStatus()).isEqualTo(JobStatus.DRAFT);
    }

    @Test
    @DisplayName("Tim kiem trong hang cho bi chan khi tu khoa dai hon 100 ky tu")
    void moderationQueue_searchLongerThan100Chars_returnsBadRequest() {
        assertThatThrownBy(() -> service.moderationQueue(0, 20, null, "x".repeat(101)))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Từ khóa tìm kiếm tối đa 100 ký tự");
    }

    @Test
    @DisplayName("DELETE /jobs/{id} la xoa mem: set deletedAt chu khong xoa hang")
    void deleteJob_setsDeletedAtInsteadOfRemovingRow() {
        signIn(employer);
        JobDescription job = givenJob(JobStatus.DRAFT);

        service.deleteJob(JOB_ID);

        assertThat(job.getDeletedAt()).isNotNull();
        verify(jobRepository).save(job);
        verify(jobRepository, never()).delete(any(JobDescription.class));
    }

    @Test
    @DisplayName("Goi y ung vien chi doc tin cua chinh minh")
    void getMatchingCandidates_byNonOwner_returnsBadRequest() {
        signIn(stranger);
        givenJob(JobStatus.OPEN);

        assertThatThrownBy(() -> service.getMatchingCandidates(JOB_ID))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Bạn không có quyền thao tác trên tin tuyển dụng này");
        verifyNoInteractions(matchingService);
    }

    private void signIn(User user) {
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(user.getEmail(), null));
    }

    private JobDescription givenJob(JobStatus status) {
        JobDescription job = JobDescription.builder()
                .id(JOB_ID)
                .employer(employer)
                .title("Senior Backend Engineer")
                .companyName("InterviewAI Platform")
                .description("Build and operate the interview platform")
                .requirements("5+ years Java")
                .requiredSkills("Java,Spring Boot")
                .status(status)
                .build();
        when(jobRepository.findByIdAndDeletedAtIsNull(JOB_ID)).thenReturn(Optional.of(job));
        return job;
    }

    private JobModerationRecord capturedModerationRecord() {
        ArgumentCaptor<JobModerationRecord> captor = ArgumentCaptor.forClass(JobModerationRecord.class);
        verify(moderationRecordRepository).save(captor.capture());
        return captor.getValue();
    }

    private JobRequest request() {
        JobRequest request = new JobRequest();
        request.setTitle("Senior Backend Engineer");
        request.setCompanyName("InterviewAI Platform");
        request.setLocation("Remote");
        request.setDescription("Build and operate the interview platform");
        request.setRequirements("5+ years Java");
        request.setRequiredSkills("Java,Spring Boot");
        return request;
    }

    private JobReviewRequest review(boolean approved, String reason) {
        JobReviewRequest request = new JobReviewRequest();
        request.setApproved(approved);
        request.setReason(reason);
        return request;
    }

    private User user(Long id, String email, String fullName, Role role) {
        return User.builder()
                .id(id)
                .email(email)
                .password("encoded")
                .fullName(fullName)
                .role(role)
                .locked(false)
                .build();
    }
}
