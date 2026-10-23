package com.aiinterview.platform.service.impl;

import com.aiinterview.platform.common.exception.BadRequestException;
import com.aiinterview.platform.common.exception.ResourceNotFoundException;
import com.aiinterview.platform.model.dto.request.ApplyJobRequest;
import com.aiinterview.platform.model.dto.request.BookSlotRequest;
import com.aiinterview.platform.model.dto.request.EvaluateApplicationRequest;
import com.aiinterview.platform.model.dto.response.ApplicationDetailResponse;
import com.aiinterview.platform.model.dto.response.ApplicationFunnelResponse;
import com.aiinterview.platform.model.dto.response.ApplicationResponse;
import com.aiinterview.platform.model.dto.response.ApplicationStatusTimelineEntry;
import com.aiinterview.platform.model.entity.Application;
import com.aiinterview.platform.model.entity.ApplicationStatusHistory;
import com.aiinterview.platform.model.entity.InterviewSlot;
import com.aiinterview.platform.model.entity.JobDescription;
import com.aiinterview.platform.model.entity.User;
import com.aiinterview.platform.model.enums.ApplicationStatus;
import com.aiinterview.platform.model.enums.JobStatus;
import com.aiinterview.platform.model.enums.Role;
import com.aiinterview.platform.model.repository.ApplicationRepository;
import com.aiinterview.platform.model.repository.ApplicationStatusHistoryRepository;
import com.aiinterview.platform.model.repository.InterviewSlotRepository;
import com.aiinterview.platform.model.repository.JobDescriptionRepository;
import com.aiinterview.platform.model.repository.UserRepository;
import com.aiinterview.platform.security.utils.SecurityUtils;
import com.aiinterview.platform.service.ApplicationService;
import com.aiinterview.platform.service.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class ApplicationServiceImpl implements ApplicationService {

    /** Thứ tự các vòng trong phễu (FR-ASM-03). REJECTED/CANCELLED là quyết định cuối, không phải một vòng. */
    private static final List<ApplicationStatus> FUNNEL_STAGES = List.of(
            ApplicationStatus.PENDING,
            ApplicationStatus.SHORTLISTED,
            ApplicationStatus.SCHEDULED,
            ApplicationStatus.INTERVIEWED,
            ApplicationStatus.ACCEPTED);

    private final ApplicationRepository applicationRepository;
    private final ApplicationStatusHistoryRepository historyRepository;
    private final JobDescriptionRepository jobRepository;
    private final InterviewSlotRepository slotRepository;
    private final UserRepository userRepository;
    private final EmailService emailService;

    @Override
    @Transactional
    public ApplicationResponse apply(Long jobId, ApplyJobRequest request) {
        User candidate = SecurityUtils.getCurrentUser(userRepository);
        JobDescription job = jobRepository.findByIdAndDeletedAtIsNull(jobId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tin tuyển dụng với id: " + jobId));
        if (job.getStatus() != JobStatus.OPEN) {
            throw new BadRequestException("Tin tuyển dụng này đã đóng");
        }
        boolean alreadyApplied = applicationRepository
                .existsByCandidateAndJobAndStatusNot(candidate, job, ApplicationStatus.CANCELLED);
        if (alreadyApplied) {
            throw new BadRequestException("Bạn đã ứng tuyển vào vị trí này");
        }
        Application application = Application.builder()
                .candidate(candidate)
                .job(job)
                .coverLetter(request != null ? request.getCoverLetter() : null)
                .status(ApplicationStatus.PENDING)
                .build();
        Application saved = applicationRepository.save(application);
        recordHistory(saved, null, ApplicationStatus.PENDING, candidate, null);
        try {
            emailService.sendApplicationReceivedEmail(
                    job.getEmployer().getEmail(), candidate.getFullName(), job.getTitle());
        } catch (Exception e) {
            log.warn("Không gửi được email thông báo ứng tuyển", e);
        }
        return ApplicationResponse.from(saved);
    }

    @Override
    public Page<ApplicationResponse> getMyApplications(int page, int size) {
        User candidate = SecurityUtils.getCurrentUser(userRepository);
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return applicationRepository.findByCandidateOrderByCreatedAtDesc(candidate, pageable)
                .map(ApplicationResponse::from);
    }

    @Override
    @Transactional
    public void cancelApplication(Long applicationId) {
        User candidate = SecurityUtils.getCurrentUser(userRepository);
        Application application = applicationRepository.findByIdAndCandidate(applicationId, candidate)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn ứng tuyển với id: " + applicationId));
        if (application.getStatus() == ApplicationStatus.INTERVIEWED
                || application.getStatus() == ApplicationStatus.ACCEPTED
                || application.getStatus() == ApplicationStatus.REJECTED) {
            throw new BadRequestException("Không thể hủy đơn ở trạng thái " + application.getStatus());
        }
        ApplicationStatus from = application.getStatus();
        application.setStatus(ApplicationStatus.CANCELLED);
        // Hủy đơn mà vẫn để slot mang bookedBy thì khung giờ chết hẳn: không ai đặt lại được,
        // employer còn thấy một buổi sẽ không bao giờ diễn ra.
        if (application.getSlot() != null) {
            releaseSlot(application.getSlot());
            application.setSlot(null);
        }
        applicationRepository.save(application);
        recordHistory(application, from, ApplicationStatus.CANCELLED, candidate, null);
    }

    @Override
    public Page<ApplicationResponse> getJobApplications(Long jobId, int page, int size) {
        JobDescription job = findOwnedJobOrThrow(jobId);
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return applicationRepository.findByJobOrderByCreatedAtDesc(job, pageable)
                .map(ApplicationResponse::from);
    }

    @Override
    @Transactional
    public ApplicationResponse evaluate(Long applicationId, EvaluateApplicationRequest request) {
        Application application = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn ứng tuyển với id: " + applicationId));
        User current = SecurityUtils.getCurrentUser(userRepository);
        boolean isOwner = application.getJob().getEmployer().getId().equals(current.getId());
        boolean isAdmin = current.getRole() == Role.ROLE_ADMIN || current.getRole() == Role.ROLE_STAFF;
        if (!isOwner && !isAdmin) {
            throw new BadRequestException("Bạn không có quyền đánh giá đơn ứng tuyển này");
        }
        ApplicationStatus from = application.getStatus();
        ApplicationStatus to = request.getStatus();
        boolean statusChanges = from != to;
        if (statusChanges) {
            validateTransition(from, to);
        }

        application.setStatus(to);
        application.setScore(request.getScore());
        application.setFeedback(request.getFeedback());
        Application saved = applicationRepository.save(application);

        // Timeline chỉ nhận dòng khi vòng thật sự đổi; sửa lại điểm ở cùng vòng không nhân bản dòng lịch sử.
        if (statusChanges) {
            recordHistory(saved, from, to, current, request.getNote());
        }

        try {
            emailService.sendApplicationStatusEmail(
                    application.getCandidate().getEmail(),
                    application.getCandidate().getFullName(),
                    application.getJob().getTitle(),
                    to.name());
        } catch (Exception e) {
            log.warn("Không gửi được email cập nhật trạng thái", e);
        }
        return ApplicationResponse.from(saved);
    }

    @Override
    @Transactional
    public ApplicationResponse bookSlot(BookSlotRequest request) {
        User candidate = SecurityUtils.getCurrentUser(userRepository);
        InterviewSlot slot = slotRepository.findByIdAndCancelledFalse(request.getSlotId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy khung giờ với id: " + request.getSlotId()));

        // re-check inside the transaction to prevent double booking
        if (slot.getBookedBy() != null) {
            throw new BadRequestException("Khung giờ này đã có người đặt");
        }
        if (slot.getStartTime().isBefore(LocalDateTime.now())) {
            throw new BadRequestException("Khung giờ này đã qua");
        }

        Application application;
        if (request.getApplicationId() != null) {
            application = applicationRepository.findByIdAndCandidate(request.getApplicationId(), candidate)
                    .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn ứng tuyển với id: " + request.getApplicationId()));
            if (!application.getJob().getEmployer().getId().equals(slot.getEmployer().getId())) {
                throw new BadRequestException("Khung giờ không thuộc người phỏng vấn của đơn này");
            }
        } else {
            // booking without a prior application is not allowed; the candidate must apply first
            throw new BadRequestException("Vui lòng ứng tuyển trước khi đặt lịch");
        }

        if (application.getStatus() != ApplicationStatus.PENDING && application.getStatus() != ApplicationStatus.SCHEDULED) {
            throw new BadRequestException("Đơn ứng tuyển không ở trạng thái có thể đặt lịch");
        }
        ApplicationStatus from = application.getStatus();

        slot.setBookedBy(candidate);
        slot.setBookedAt(LocalDateTime.now());
        slotRepository.save(slot);

        application.setSlot(slot);
        application.setStatus(ApplicationStatus.SCHEDULED);
        Application saved = applicationRepository.save(application);
        if (from != ApplicationStatus.SCHEDULED) {
            recordHistory(saved, from, ApplicationStatus.SCHEDULED, candidate, null);
        }

        String timeLabel = slot.getStartTime().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"));
        try {
            emailService.sendSlotBookedEmail(candidate.getEmail(), candidate.getFullName(),
                    application.getJob().getTitle(), timeLabel, "Ứng viên");
            emailService.sendSlotBookedEmail(slot.getEmployer().getEmail(), slot.getEmployer().getFullName(),
                    application.getJob().getTitle(), timeLabel, "Người phỏng vấn");
        } catch (Exception e) {
            log.warn("Không gửi được email xác nhận lịch", e);
        }
        return ApplicationResponse.from(saved);
    }

    /**
     * FR-SCH-02: hủy riêng buổi đã đặt. Dòng lịch sử SCHEDULED -> PENDING ghi thẳng ở đây, không đi qua
     * validateTransition của evaluate(), nên employer vẫn không dùng /evaluate để lùi vòng tùy ý.
     */
    @Override
    @Transactional
    public ApplicationResponse unschedule(Long applicationId) {
        User current = SecurityUtils.getCurrentUser(userRepository);
        Application application = scheduledApplicationOrThrow(applicationId, current);
        InterviewSlot slot = application.getSlot();
        if (slot.getStartTime().isBefore(LocalDateTime.now())) {
            throw new BadRequestException("Buổi phỏng vấn đã diễn ra, không hủy lịch được");
        }

        releaseSlot(slot);
        application.setSlot(null);
        application.setStatus(ApplicationStatus.PENDING);
        Application saved = applicationRepository.save(application);
        recordHistory(saved, ApplicationStatus.SCHEDULED, ApplicationStatus.PENDING, current, null);
        return ApplicationResponse.from(saved);
    }

    @Override
    @Transactional
    public ApplicationResponse reschedule(Long applicationId, Long slotId) {
        User current = SecurityUtils.getCurrentUser(userRepository);
        Application application = scheduledApplicationOrThrow(applicationId, current);
        InterviewSlot currentSlot = application.getSlot();
        if (currentSlot.getStartTime().isBefore(LocalDateTime.now())) {
            throw new BadRequestException("Buổi phỏng vấn đã diễn ra, không đổi lịch được");
        }

        InterviewSlot target = slotRepository.findByIdAndCancelledFalse(slotId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy khung giờ với id: " + slotId));
        if (target.getId().equals(currentSlot.getId())) {
            throw new BadRequestException("Khung giờ mới trùng khung giờ hiện tại");
        }
        if (target.getBookedBy() != null) {
            throw new BadRequestException("Khung giờ này đã có người đặt");
        }
        if (target.getStartTime().isBefore(LocalDateTime.now())) {
            throw new BadRequestException("Khung giờ này đã qua");
        }
        if (!target.getEmployer().getId().equals(currentSlot.getEmployer().getId())) {
            throw new BadRequestException("Khung giờ không thuộc người phỏng vấn của đơn này");
        }

        releaseSlot(currentSlot);
        // Người thao tác có thể là employer/admin, nhưng slot luôn thuộc về ứng viên của đơn.
        target.setBookedBy(application.getCandidate());
        target.setBookedAt(LocalDateTime.now());
        slotRepository.save(target);

        application.setSlot(target);
        Application saved = applicationRepository.save(application);

        String timeLabel = target.getStartTime().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"));
        try {
            emailService.sendSlotBookedEmail(application.getCandidate().getEmail(),
                    application.getCandidate().getFullName(), application.getJob().getTitle(),
                    timeLabel, "Ứng viên");
            emailService.sendSlotBookedEmail(target.getEmployer().getEmail(),
                    target.getEmployer().getFullName(), application.getJob().getTitle(),
                    timeLabel, "Người phỏng vấn");
        } catch (Exception e) {
            log.warn("Không gửi được email xác nhận đổi lịch", e);
        }
        return ApplicationResponse.from(saved);
    }

    /** Đơn phải đang có lịch và actor phải là ứng viên, người phỏng vấn của buổi đó, hoặc admin/staff. */
    private Application scheduledApplicationOrThrow(Long applicationId, User current) {
        Application application = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn ứng tuyển với id: " + applicationId));
        if (application.getStatus() != ApplicationStatus.SCHEDULED || application.getSlot() == null) {
            throw new BadRequestException("Đơn ứng tuyển chưa có lịch phỏng vấn");
        }
        boolean isCandidate = application.getCandidate().getId().equals(current.getId());
        boolean isInterviewer = application.getSlot().getEmployer().getId().equals(current.getId());
        boolean isStaff = current.getRole() == Role.ROLE_ADMIN || current.getRole() == Role.ROLE_STAFF;
        if (!isCandidate && !isInterviewer && !isStaff) {
            throw new BadRequestException("Bạn không có quyền quản lý lịch phỏng vấn này");
        }
        return application;
    }

    /** Khung giờ sau khi thả bookedBy trở lại danh sách trống cho ứng viên khác chọn. */
    private void releaseSlot(InterviewSlot slot) {
        slot.setBookedBy(null);
        slot.setBookedAt(null);
        slotRepository.save(slot);
    }

    @Override
    public Page<ApplicationResponse> adminListApplications(int page, int size) {
        User current = SecurityUtils.getCurrentUser(userRepository);
        if (current.getRole() != Role.ROLE_ADMIN && current.getRole() != Role.ROLE_STAFF) {
            throw new BadRequestException("Không có quyền truy cập");
        }
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return applicationRepository.findAll(pageable).map(ApplicationResponse::from);
    }

    @Override
    @Transactional(readOnly = true)
    public ApplicationDetailResponse getApplicationDetail(Long applicationId) {
        User current = SecurityUtils.getCurrentUser(userRepository);
        Application application = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn ứng tuyển với id: " + applicationId));

        boolean isCandidate = application.getCandidate().getId().equals(current.getId());
        boolean isEmployer = application.getJob().getEmployer().getId().equals(current.getId());
        boolean isStaff = current.getRole() == Role.ROLE_ADMIN || current.getRole() == Role.ROLE_STAFF;
        if (!isCandidate && !isEmployer && !isStaff) {
            throw new BadRequestException("Bạn không có quyền xem chi tiết đơn ứng tuyển này");
        }

        // BR-09: ghi chú nội bộ và danh tính người thao tác chỉ phía tuyển dụng được thấy.
        List<ApplicationStatusHistory> history = historyRepository.findByApplicationOrderByActedAtAsc(application);
        List<ApplicationStatusTimelineEntry> timeline = new ArrayList<>(history.size());
        for (ApplicationStatusHistory row : history) {
            timeline.add(ApplicationStatusTimelineEntry.from(row, isEmployer || isStaff));
        }
        return ApplicationDetailResponse.from(application, timeline);
    }

    @Override
    @Transactional(readOnly = true)
    public ApplicationFunnelResponse getApplicationFunnel(Long jobId) {
        JobDescription job = findOwnedJobOrThrow(jobId);

        // Vòng xa nhất mỗi đơn đi tới. Trạng thái hiện tại chỉ đủ khi đơn còn tiến dần; đơn đã bị
        // từ chối/hủy mất dấu vòng trước đó nên phải lấy từ timeline, vì vậy cộng gộp cả hai nguồn.
        Map<Long, Integer> furthest = new HashMap<>();
        for (Object[] row : applicationRepository.findStatusesByJob(jobId)) {
            furthest.put((Long) row[0], rankOf((ApplicationStatus) row[1]));
        }
        for (Object[] row : historyRepository.findReachedStatusesByJob(jobId)) {
            furthest.merge((Long) row[0], rankOf(ApplicationStatus.valueOf((String) row[1])), Math::max);
        }

        long[] reached = new long[FUNNEL_STAGES.size()];
        for (Integer rank : furthest.values()) {
            for (int stage = 0; stage <= Math.min(rank, reached.length - 1); stage++) {
                reached[stage]++;
            }
        }

        List<ApplicationFunnelResponse.Stage> stages = new ArrayList<>();
        for (int i = 0; i < FUNNEL_STAGES.size(); i++) {
            Long advanced = i + 1 < FUNNEL_STAGES.size() ? reached[i + 1] : null;
            stages.add(ApplicationFunnelResponse.Stage.builder()
                    .status(FUNNEL_STAGES.get(i))
                    .reached(reached[i])
                    .advanced(advanced)
                    .passRate(advanced == null || reached[i] == 0 ? null : (double) advanced / reached[i])
                    .build());
        }

        return ApplicationFunnelResponse.builder()
                .jobId(job.getId())
                .jobTitle(job.getTitle())
                .totalApplications((long) furthest.size())
                .stages(stages)
                .build();
    }

    /** REJECTED/CANCELLED không phải một vòng; chúng chỉ chứng minh đơn từng nộp. */
    private static int rankOf(ApplicationStatus status) {
        int rank = FUNNEL_STAGES.indexOf(status);
        return rank < 0 ? 0 : rank;
    }

    private void recordHistory(Application application, ApplicationStatus from, ApplicationStatus to,
                               User actor, String note) {
        historyRepository.save(ApplicationStatusHistory.builder()
                .application(application)
                .fromStatus(from == null ? null : from.name())
                .toStatus(to.name())
                .actor(actor)
                .note(note)
                .build());
    }

    private void validateTransition(ApplicationStatus from, ApplicationStatus to) {
        if (to == null) {
            throw new BadRequestException("Trạng thái không hợp lệ");
        }
        boolean valid = switch (from) {
            case PENDING -> to == ApplicationStatus.SHORTLISTED
                    || to == ApplicationStatus.SCHEDULED
                    || to == ApplicationStatus.REJECTED;
            case SHORTLISTED -> to == ApplicationStatus.SCHEDULED || to == ApplicationStatus.REJECTED;
            case SCHEDULED -> to == ApplicationStatus.INTERVIEWED || to == ApplicationStatus.REJECTED;
            case INTERVIEWED -> to == ApplicationStatus.ACCEPTED || to == ApplicationStatus.REJECTED;
            default -> false;
        };
        if (!valid) {
            throw new BadRequestException("Không thể chuyển trạng thái từ " + from + " sang " + to);
        }
    }

    private JobDescription findOwnedJobOrThrow(Long jobId) {
        User current = SecurityUtils.getCurrentUser(userRepository);
        JobDescription job = jobRepository.findByIdAndDeletedAtIsNull(jobId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tin tuyển dụng với id: " + jobId));
        boolean isOwner = job.getEmployer().getId().equals(current.getId());
        boolean isAdmin = current.getRole() == Role.ROLE_ADMIN || current.getRole() == Role.ROLE_STAFF;
        if (!isOwner && !isAdmin) {
            throw new BadRequestException("Bạn không có quyền xem danh sách ứng viên của tin này");
        }
        return job;
    }
}
