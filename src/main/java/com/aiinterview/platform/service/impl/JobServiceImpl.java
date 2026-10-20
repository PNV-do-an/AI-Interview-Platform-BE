package com.aiinterview.platform.service.impl;

import com.aiinterview.platform.common.exception.BadRequestException;
import com.aiinterview.platform.common.exception.ResourceNotFoundException;
import com.aiinterview.platform.model.dto.request.JobRequest;
import com.aiinterview.platform.model.dto.request.JobReviewRequest;
import com.aiinterview.platform.model.dto.response.JobDetailResponse;
import com.aiinterview.platform.model.dto.response.JobSummaryResponse;
import com.aiinterview.platform.model.dto.response.MatchResponse;
import com.aiinterview.platform.model.entity.AdminAuditLog;
import com.aiinterview.platform.model.entity.JobDescription;
import com.aiinterview.platform.model.entity.User;
import com.aiinterview.platform.model.enums.AuditAction;
import com.aiinterview.platform.model.enums.JobStatus;
import com.aiinterview.platform.model.enums.Role;
import com.aiinterview.platform.model.repository.AdminAuditLogRepository;
import com.aiinterview.platform.model.repository.JobDescriptionRepository;
import com.aiinterview.platform.model.repository.UserRepository;
import com.aiinterview.platform.security.utils.SecurityUtils;
import com.aiinterview.platform.service.JobService;
import com.aiinterview.platform.service.MatchingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
        job.setStatus(JobStatus.PENDING_APPROVAL);
        return JobDetailResponse.from(jobRepository.save(job));
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
        AuditAction action;
        if (approved) {
            job.setStatus(JobStatus.OPEN);
            job.setReviewNote(null);
            action = AuditAction.APPROVE_JOB;
        } else {
            job.setStatus(JobStatus.DRAFT);
            job.setReviewNote(request.getReason().trim());
            action = AuditAction.REJECT_JOB;
        }
        jobRepository.save(job);
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

    @Override
    public Page<JobSummaryResponse> adminListJobs(int page, int size, String search) {
        User admin = SecurityUtils.getCurrentUser(userRepository);
        if (admin.getRole() != Role.ROLE_ADMIN && admin.getRole() != Role.ROLE_STAFF) {
            throw new BadRequestException("Không có quyền truy cập");
        }
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        String searchParam = (search != null && !search.isBlank()) ? search.trim() : null;
        return jobRepository.searchAllJobs(searchParam, pageable).map(JobSummaryResponse::from);
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
