package com.aiinterview.platform.service.impl;

import com.aiinterview.platform.common.exception.BadRequestException;
import com.aiinterview.platform.common.exception.ResourceNotFoundException;
import com.aiinterview.platform.model.dto.request.JobRequest;
import com.aiinterview.platform.model.dto.request.JobReviewRequest;
import com.aiinterview.platform.model.dto.response.JobDetailResponse;
import com.aiinterview.platform.model.dto.response.JobModerationHistoryResponse;
import com.aiinterview.platform.model.dto.response.JobSummaryResponse;
import com.aiinterview.platform.model.dto.response.MatchResponse;
import com.aiinterview.platform.model.dto.response.ModerationHistoryStatsResponse;
import com.aiinterview.platform.model.dto.response.ModerationJobDetailResponse;
import com.aiinterview.platform.model.dto.response.ModerationJobResponse;
import com.aiinterview.platform.model.dto.response.ModerationQueueStatsResponse;
import com.aiinterview.platform.model.dto.response.ModerationRecordResponse;
import com.aiinterview.platform.model.entity.AdminAuditLog;
import com.aiinterview.platform.model.entity.JobDescription;
import com.aiinterview.platform.model.entity.JobModerationRecord;
import com.aiinterview.platform.model.entity.User;
import com.aiinterview.platform.model.enums.AuditAction;
import com.aiinterview.platform.model.enums.JobStatus;
import com.aiinterview.platform.model.enums.ModerationAction;
import com.aiinterview.platform.model.enums.ModerationActorRole;
import com.aiinterview.platform.model.enums.Role;
import com.aiinterview.platform.model.repository.AdminAuditLogRepository;
import com.aiinterview.platform.model.repository.JobDescriptionRepository;
import com.aiinterview.platform.model.repository.JobModerationRecordRepository;
import com.aiinterview.platform.model.repository.UserRepository;
import com.aiinterview.platform.security.utils.SecurityUtils;
import com.aiinterview.platform.service.JobService;
import com.aiinterview.platform.service.MatchingService;
import com.aiinterview.platform.service.ai.JdPolicyScreener;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class JobServiceImpl implements JobService {

    private final JobDescriptionRepository jobRepository;
    private final UserRepository userRepository;
    private final MatchingService matchingService;
    private final AdminAuditLogRepository auditLogRepository;
    private final JobModerationRecordRepository moderationRecordRepository;

    @Override
    public Page<JobSummaryResponse> searchJobs(int page, int size, String search) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        String searchParam = (search != null && !search.isBlank()) ? search.trim() : null;
        return jobRepository.searchOpenJobs(searchParam, JobStatus.OPEN, pageable)
                .map(JobSummaryResponse::from);
    }

    @Override
    public JobDetailResponse getJobDetail(Long jobId) {
        JobDescription job = findJobOrThrow(jobId);
        if (job.getStatus() != JobStatus.OPEN && !canManage(job, null)) {
            // Bản nháp/chờ duyệt không được lộ công khai: người lạ nhìn thấy như "không tồn tại".
            throw new ResourceNotFoundException("Không tìm thấy tin tuyển dụng với id: " + jobId);
        }
        return JobDetailResponse.from(job);
    }

    @Override
    public Page<JobSummaryResponse> getEmployerJobs(int page, int size) {
        User employer = SecurityUtils.getCurrentUser(userRepository);
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return jobRepository.findByEmployerAndDeletedAtIsNull(employer, pageable)
                .map(JobSummaryResponse::from);
    }

    @Override
    @Transactional
    public JobDetailResponse createJob(JobRequest request) {
        validateWizardPayload(request);
        User employer = SecurityUtils.getCurrentUser(userRepository);
        JobDescription job = new JobDescription();
        applyWizardFields(job, request);
        job.setEmployer(employer);
        job.setStatus(JobStatus.DRAFT);
        return JobDetailResponse.from(jobRepository.save(job));
    }

    @Override
    @Transactional
    public JobDetailResponse updateJob(Long jobId, JobRequest request) {
        JobDescription job = findOwnedJobOrThrow(jobId);
        if (job.getStatus() == JobStatus.PENDING_APPROVAL) {
            throw new BadRequestException("Tin đang chờ duyệt, không thể sửa");
        }
        validateWizardPayload(request);
        applyWizardFields(job, request);
        if (job.getAiSafetyScore() != null) {
            // Nội dung đổi thì điểm đã lưu phải đổi theo, nếu không cột ai_safety_score kể
            // về một bản tin cũ. Bản nháp chưa nộp vẫn để trống, submit sẽ ghi điểm đầu tiên.
            job.setAiSafetyScore(JdPolicyScreener.screen(job).score());
        }
        return JobDetailResponse.from(jobRepository.save(job));
    }

    @Override
    @Transactional
    public JobDetailResponse changeStatus(Long jobId, JobStatus status) {
        if (status == null) {
            throw new BadRequestException("Trạng thái không hợp lệ");
        }
        JobDescription job = findOwnedJobOrThrow(jobId);
        boolean openClosedPair = (job.getStatus() == JobStatus.OPEN && status == JobStatus.CLOSED)
                || (job.getStatus() == JobStatus.CLOSED && status == JobStatus.OPEN);
        if (!openClosedPair) {
            // OPEN chỉ đạt qua submit + duyệt; DRAFT/PENDING_APPROVAL không điều khiển bằng tay.
            throw new BadRequestException("Trạng thái không hợp lệ cho tin tuyển dụng này");
        }
        job.setStatus(status);
        return JobDetailResponse.from(jobRepository.save(job));
    }

    @Override
    @Transactional
    public JobDetailResponse submitForReview(Long jobId) {
        JobDescription job = findOwnedJobOrThrow(jobId);
        if (job.getStatus() != JobStatus.DRAFT) {
            throw new BadRequestException("Chỉ bản nháp mới gửi duyệt được");
        }
        LocalDateTime submittedAt = LocalDateTime.now();
        job.setStatus(JobStatus.PENDING_APPROVAL);
        job.setSubmittedAt(submittedAt);
        // 009 FR-006: rule-based pre-screen, the column's first writer. Reviewers see the same
        // numbers again through moderationDetail(), because the rules only read stored fields.
        job.setAiSafetyScore(JdPolicyScreener.screen(job).score());
        job = jobRepository.save(job);
        boolean resubmitted = moderationRecordRepository.existsByJobIdAndAction(jobId, ModerationAction.REJECTED);
        moderationRecordRepository.save(JobModerationRecord.builder()
                .job(job)
                .actor(job.getEmployer())
                .actorRole(ModerationActorRole.EMPLOYER)
                .action(resubmitted ? ModerationAction.RESUBMITTED : ModerationAction.SUBMITTED)
                .build());
        return JobDetailResponse.from(job);
    }

    @Override
    @Transactional
    public JobDetailResponse withdrawFromReview(Long jobId) {
        JobDescription job = findOwnedJobOrThrow(jobId);
        if (job.getStatus() != JobStatus.PENDING_APPROVAL) {
            throw new BadRequestException("Chỉ tin đang chờ duyệt mới rút được");
        }
        job.setStatus(JobStatus.DRAFT);
        // Đồng hồ hàng chờ tắt: lần nộp lại sẽ tính từ mốc submit mới.
        job.setSubmittedAt(null);
        job = jobRepository.save(job);
        moderationRecordRepository.save(JobModerationRecord.builder()
                .job(job)
                .actor(job.getEmployer())
                .actorRole(ModerationActorRole.EMPLOYER)
                .action(ModerationAction.WITHDRAWN)
                .build());
        return JobDetailResponse.from(job);
    }

    @Override
    @Transactional
    public JobDetailResponse reviewJob(Long jobId, JobReviewRequest request) {
        User reviewer = SecurityUtils.getCurrentUser(userRepository);
        JobDescription job = findJobOrThrow(jobId);
        if (job.getStatus() != JobStatus.PENDING_APPROVAL) {
            throw new BadRequestException("Tin không ở trạng thái chờ duyệt");
        }
        boolean approved = Boolean.TRUE.equals(request.getApproved());
        if (!approved && (request.getReason() == null || request.getReason().isBlank())) {
            throw new BadRequestException("Vui lòng nêu lý do từ chối");
        }
        LocalDateTime decidedAt = LocalDateTime.now();
        // Captured before the reject branch clears it, so a REJECTED row still carries its wait time.
        LocalDateTime submittedAt = job.getSubmittedAt();
        AuditAction action;
        ModerationAction moderationAction;
        String note = null;
        if (approved) {
            job.setStatus(JobStatus.OPEN);
            job.setReviewNote(null);
            action = AuditAction.APPROVE_JOB;
            moderationAction = ModerationAction.APPROVED;
        } else {
            job.setStatus(JobStatus.DRAFT);
            job.setReviewNote(request.getReason().trim());
            // The draft must be submitted again, so the queue clock restarts from the resubmit.
            job.setSubmittedAt(null);
            action = AuditAction.REJECT_JOB;
            moderationAction = ModerationAction.REJECTED;
            note = request.getReason().trim();
        }
        job.setDecidedAt(decidedAt);
        job = jobRepository.save(job);
        moderationRecordRepository.save(JobModerationRecord.builder()
                .job(job)
                .actor(reviewer)
                .actorRole(toActorRole(reviewer))
                .action(moderationAction)
                .note(note)
                .processingSeconds(processingSeconds(submittedAt, decidedAt))
                .build());
        String detail = "job #" + job.getId() + ": " + job.getTitle()
                + (approved ? "" : " — Lý do: " + job.getReviewNote());
        auditLogRepository.save(AdminAuditLog.builder()
                .adminId(reviewer.getId())
                .adminEmail(reviewer.getEmail())
                .action(action.name())
                .detail(detail)
                .succeeded(true)
                .build());
        return JobDetailResponse.from(job);
    }

    @Override
    @Transactional
    public void deleteJob(Long jobId) {
        JobDescription job = findOwnedJobOrThrow(jobId);
        job.setDeletedAt(LocalDateTime.now());
        jobRepository.save(job);
    }

    // ── moderation console (spec 004) ────────────────────────────────────────

    @Override
    public Page<ModerationJobResponse> moderationQueue(int page, int size, JobStatus status, String search) {
        JobStatus effective = status == null ? JobStatus.PENDING_APPROVAL : status;
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return jobRepository.searchModerationQueue(effective, escapeSearch(search), pageable)
                .map(ModerationJobResponse::from);
    }

    @Override
    public ModerationJobDetailResponse moderationDetail(Long jobId) {
        JobDescription job = findJobOrThrow(jobId);
        List<ModerationRecordResponse> timeline = moderationRecordRepository
                .findByJobIdOrderByCreatedAtDesc(jobId).stream()
                .map(ModerationRecordResponse::from)
                .toList();
        JdPolicyScreener.Screening screening = JdPolicyScreener.screen(job);
        return ModerationJobDetailResponse.builder()
                .job(JobDetailResponse.from(job))
                .submittedAt(job.getSubmittedAt())
                .decidedAt(job.getDecidedAt())
                .aiSafetyScore(job.getAiSafetyScore())
                .aiSafety(ModerationJobDetailResponse.AiSafety.builder()
                        .score(screening.score())
                        .checks(screening.checks().stream()
                                .map(c -> ModerationJobDetailResponse.SafetyCheck.builder()
                                        .key(c.key()).label(c.label())
                                        .passed(c.passed()).note(c.note()).build())
                                .toList())
                        .build())
                .employer(employerSnapshot(job.getEmployer(), job.getCompanyName()))
                .timeline(timeline)
                .build();
    }

    /** 009 FR-005: everything the reviewer needs to judge the account behind a posting. */
    private ModerationJobDetailResponse.Employer employerSnapshot(User employer, String companyName) {
        long total = jobRepository.countByEmployerIdAndDeletedAtIsNull(employer.getId());
        long pending = jobRepository.countByEmployerIdAndDeletedAtIsNullAndStatus(
                employer.getId(), JobStatus.PENDING_APPROVAL);
        long approved = moderationRecordRepository.countDistinctJobsOfEmployerByAction(
                ModerationAction.APPROVED, employer.getId());
        long rejected = moderationRecordRepository.countDistinctJobsOfEmployerByAction(
                ModerationAction.REJECTED, employer.getId());
        long decided = approved + rejected;
        return ModerationJobDetailResponse.Employer.builder()
                .id(employer.getId())
                .companyName(companyName)
                .email(employer.getEmail())
                .accountCreatedAt(employer.getCreatedAt())
                .jobs(ModerationJobDetailResponse.EmployerJobs.builder()
                        .total(total).approved(approved).rejected(rejected).pending(pending).build())
                // No decision yet means no evidence either way — null, never a default 100.
                .trustScore(decided == 0 ? null : (int) Math.round(100.0 * approved / decided))
                .build();
    }

    @Override
    public ModerationQueueStatsResponse moderationQueueStats() {
        long pending = jobRepository.countByDeletedAtIsNullAndStatus(JobStatus.PENDING_APPROVAL);
        return ModerationQueueStatsResponse.builder()
                .pending(pending)
                .draft(jobRepository.countByDeletedAtIsNullAndStatus(JobStatus.DRAFT))
                .approved(jobRepository.countByDeletedAtIsNullAndStatus(JobStatus.OPEN))
                .rejected(moderationRecordRepository.countDistinctJobsByAction(ModerationAction.REJECTED))
                .total(jobRepository.countByDeletedAtIsNullAndStatus(JobStatus.CLOSED)
                        + pending
                        + jobRepository.countByDeletedAtIsNullAndStatus(JobStatus.DRAFT)
                        + jobRepository.countByDeletedAtIsNullAndStatus(JobStatus.OPEN))
                .build();
    }

    @Override
    public Page<JobModerationHistoryResponse> moderationHistory(int page, int size, ModerationAction decision,
                                                                LocalDate from, LocalDate to,
                                                                Long moderatorId, String search) {
        if (decision != null && decision != ModerationAction.APPROVED && decision != ModerationAction.REJECTED) {
            throw new BadRequestException("Lịch sử chỉ có hai loại quyết định: APPROVED hoặc REJECTED");
        }
        LocalDateTime fromTime = from == null ? null : from.atStartOfDay();
        LocalDateTime toTime = to == null ? null : to.atTime(23, 59, 59);
        if (fromTime != null && toTime != null && fromTime.isAfter(toTime)) {
            throw new BadRequestException("from không được lớn hơn to");
        }
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return moderationRecordRepository
                .searchDecisions(decision, fromTime, toTime, moderatorId, escapeSearch(search), pageable)
                .map(JobModerationHistoryResponse::from);
    }

    @Override
    public ModerationHistoryStatsResponse moderationHistoryStats() {
        long approved = moderationRecordRepository.countByAction(ModerationAction.APPROVED);
        long rejected = moderationRecordRepository.countByAction(ModerationAction.REJECTED);
        return new ModerationHistoryStatsResponse(approved + rejected, approved, rejected,
                moderationRecordRepository.avgProcessingSeconds());
    }

    private static ModerationActorRole toActorRole(User user) {
        if (user.getRole() == Role.ROLE_ADMIN) {
            return ModerationActorRole.ADMIN;
        }
        if (user.getRole() == Role.ROLE_STAFF) {
            return ModerationActorRole.STAFF;
        }
        return ModerationActorRole.EMPLOYER;
    }

    /** Seconds between submit and decision; negative or missing clocks are treated as unknown. */
    private static Long processingSeconds(LocalDateTime submittedAt, LocalDateTime decidedAt) {
        if (submittedAt == null) {
            return null;
        }
        long seconds = Duration.between(submittedAt, decidedAt).getSeconds();
        return seconds < 0 ? null : seconds;
    }

    /** Same LIKE-escaping rule as the admin user list, so "%" cannot scan the whole table. */
    private static String escapeSearch(String search) {
        if (search == null || search.isBlank()) {
            return null;
        }
        String trimmed = search.trim();
        if (trimmed.length() > 100) {
            throw new BadRequestException("Từ khóa tìm kiếm tối đa 100 ký tự");
        }
        return trimmed.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    @Override
    public List<MatchResponse> getMatchingCandidates(Long jobId) {
        JobDescription job = findOwnedJobOrThrow(jobId);
        return matchingService.matchCandidates(job);
    }

    private void validateWizardPayload(JobRequest request) {
        if (request.getSalaryMin() != null && request.getSalaryMax() != null
                && request.getSalaryMin() > request.getSalaryMax()) {
            throw new BadRequestException("Mức lương tối thiểu không được lớn hơn mức lương tối đa");
        }
        if (Boolean.TRUE.equals(request.getAiScreeningEnabled())) {
            if (request.getAiScreeningTrack() == null) {
                throw new BadRequestException("Vui lòng chọn hình thức phỏng vấn AI khi bật sàng lọc tự động");
            }
            if (request.getAiScreeningPassScore() == null) {
                throw new BadRequestException("Vui lòng đặt ngưỡng điểm đậu khi bật sàng lọc tự động");
            }
        }
    }

    private void applyWizardFields(JobDescription job, JobRequest request) {
        job.setTitle(request.getTitle());
        job.setCompanyName(request.getCompanyName());
        job.setPosition(request.getPosition());
        job.setLocation(request.getLocation());
        job.setSalaryRange(request.getSalaryRange());
        job.setDescription(request.getDescription());
        job.setRequirements(request.getRequirements());
        job.setRequiredSkills(request.getRequiredSkills());
        job.setDepartment(request.getDepartment());
        job.setExperienceLevel(request.getExperienceLevel());
        job.setEmploymentType(request.getEmploymentType());
        job.setWorkplaceModel(request.getWorkplaceModel());
        job.setTimezoneRequirement(request.getTimezoneRequirement());
        job.setSalaryCurrency(request.getSalaryCurrency());
        job.setSalaryMin(request.getSalaryMin());
        job.setSalaryMax(request.getSalaryMax());
        job.setEquityBand(request.getEquityBand());
        job.setAnnualBonus(request.getAnnualBonus());
        job.setAiScreeningEnabled(request.getAiScreeningEnabled());
        job.setAiScreeningTrack(request.getAiScreeningTrack());
        job.setAiScreeningPassScore(request.getAiScreeningPassScore());
        job.setAiQuestionFocusAreas(request.getAiQuestionFocusAreas());
    }

    private boolean canManage(JobDescription job, User current) {
        if (current == null) {
            current = SecurityUtils.getCurrentUser(userRepository);
        }
        boolean isOwner = job.getEmployer().getId().equals(current.getId());
        boolean isAdmin = current.getRole() == Role.ROLE_ADMIN || current.getRole() == Role.ROLE_STAFF;
        return isOwner || isAdmin;
    }

    private JobDescription findJobOrThrow(Long jobId) {
        return jobRepository.findByIdAndDeletedAtIsNull(jobId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tin tuyển dụng với id: " + jobId));
    }

    private JobDescription findOwnedJobOrThrow(Long jobId) {
        User current = SecurityUtils.getCurrentUser(userRepository);
        JobDescription job = findJobOrThrow(jobId);
        if (!canManage(job, current)) {
            throw new BadRequestException("Bạn không có quyền thao tác trên tin tuyển dụng này");
        }
        return job;
    }
}
