package com.aiinterview.platform.service.impl;

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
import com.aiinterview.platform.model.entity.User;
import com.aiinterview.platform.model.enums.AuditAction;
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
import com.aiinterview.platform.service.AdminUserService;
import com.aiinterview.platform.service.EmailService;
import com.aiinterview.platform.service.UserSessionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminUserServiceImpl implements AdminUserService {

    private static final String DELETED_MESSAGE = "Không tìm thấy tài khoản (đã bị xóa)";
    private static final int MAX_SEARCH_LENGTH = 100;
    /** Must stay equal to PasswordResetServiceImpl.RESET_TOKEN_TTL_MINUTES and the email wording. */
    private static final int RESET_TOKEN_TTL_MINUTES = 30;
    /** Same 24 hours the self-service registration link gets. */
    private static final int INVITATION_TOKEN_TTL_HOURS = 24;
    /** KPI "active in the last 30 days" window (Clarify C-1). */
    private static final int RECENT_LOGIN_WINDOW_DAYS = 30;
    private static final int MAX_EXPORT_ROWS = 10_000;
    /** The merged timeline is built in memory, so deep paging has to stop somewhere (009 FR-003). */
    private static final int MAX_ACTIVITY_WINDOW = 1_000;

    private final UserRepository userRepository;
    private final AdminAuditLogRepository auditLogRepository;
    private final LoginAttemptRepository loginAttemptRepository;
    private final RecoveryCodeRepository recoveryCodeRepository;
    private final CandidateProfileRepository candidateProfileRepository;
    private final ApplicationRepository applicationRepository;
    private final InterviewSessionRepository interviewSessionRepository;
    private final InterviewSlotRepository interviewSlotRepository;
    private final EmailService emailService;
    private final AdminAuditWriter auditWriter;
    private final PasswordEncoder passwordEncoder;
    private final UserSessionService userSessionService;
    /** Self-proxy so each bulk id runs in its own transaction (one bad id never poisons the rest). */
    private final ObjectProvider<AdminUserService> selfProvider;

    // ── reads ────────────────────────────────────────────────────────────────

    @Override
    public Page<UserSummaryResponse> getUsers(int page, int size, String search,
                                             UserStatusFilter status, Role role) {
        if (status == UserStatusFilter.DELETED) {
            throw new BadRequestException("Bộ lọc DELETED không dùng cho danh sách này, "
                    + "hãy dùng GET /api/v1/admin/users/deleted");
        }
        return withLastLogin(userRepository
                .findAllWithFilters(escapeSearch(search), role, status == null ? null : status.name(),
                        pageable(page, size)));
    }

    @Override
    public Page<UserSummaryResponse> getDeletedUsers(int page, int size, String search, Role role) {
        return withLastLogin(userRepository.findDeletedWithFilters(escapeSearch(search), role,
                pageable(page, size)));
    }

    @Override
    public AdminUserStatsResponse getStats(String search, Role role) {
        String term = escapeSearch(search);
        List<Object[]> metricRows = userRepository.countLiveMetrics(term, role);
        Object[] live = metricRows.isEmpty() ? new Object[0] : metricRows.get(0);
        return new AdminUserStatsResponse(
                asCount(at(live, 0)),
                asCount(at(live, 1)),
                asCount(at(live, 2)),
                asCount(at(live, 3)),
                asCount(at(live, 4)),
                userRepository.countDeleted(term, role),
                userRepository.countWithRecentLogin(term, role,
                        LocalDateTime.now().minusDays(RECENT_LOGIN_WINDOW_DAYS)));
    }

    @Override
    public UserDetailResponse getUserDetail(Long userId) {
        User admin = requireAdmin();
        User target = findUserOrThrow(userId);
        saveAuditLog(admin, userId, AuditAction.VIEW_USER, "Xem chi tiết tài khoản: " + target.getEmail());
        return enrichDetail(UserDetailResponse.from(target), target);
    }

    /**
     * Spec 009 FR-002: the four extra blocks of the detail screen. Each is one read of a table
     * that already exists — no derived number is invented when the source is empty.
     */
    private UserDetailResponse enrichDetail(UserDetailResponse detail, User target) {
        loginAttemptRepository.findTop1ByEmailAndSuccessTrueOrderByTimestampDesc(target.getEmail())
                .ifPresent(attempt -> {
                    detail.setLastLoginAt(attempt.getTimestamp());
                    detail.setLastIp(attempt.getIpAddress());
                });

        Double readiness = interviewSessionRepository.avgOverallScoreOf(target.getId());
        detail.setAiReadinessScore(readiness == null ? null : Math.round(readiness * 10) / 10.0);

        detail.setActivity(UserDetailResponse.Activity.builder()
                .applications(applicationRepository.countByCandidate(target))
                .mockSessions(interviewSessionRepository.countByCandidate(target))
                .bookedInterviews(interviewSlotRepository.countByBookedByAndCancelledFalse(target))
                .build());

        detail.setProfile(candidateProfileRepository.findByUser(target)
                .map(AdminUserServiceImpl::profileView)
                .orElse(null));
        return detail;
    }

    private static UserDetailResponse.Profile profileView(CandidateProfile profile) {
        return UserDetailResponse.Profile.builder()
                .skills(splitSkills(profile.getSkills()))
                .experienceYears(profile.getExperienceYears())
                .summary(profile.getSummary())
                .careerGoal(profile.getCareerGoal())
                .cvFileName(profile.getCvFileName())
                .updatedAt(profile.getUpdatedAt())
                .build();
    }

    private static List<String> splitSkills(String skills) {
        if (skills == null || skills.isBlank()) {
            return List.of();
        }
        return java.util.Arrays.stream(skills.split(","))
                .map(String::trim)
                .filter(skill -> !skill.isEmpty())
                .distinct()
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Page<UserActivityResponse> getUserActivity(Long userId, int page, int size) {
        requireAdmin();
        User target = findUserOrThrow(userId);
        if ((long) (page + 1) * size > MAX_ACTIVITY_WINDOW) {
            throw new BadRequestException("Dòng thời gian chỉ xem tới " + MAX_ACTIVITY_WINDOW + " dòng đầu");
        }

        Pageable window = PageRequest.of(0, (page + 1) * size);
        List<UserActivityResponse> events = new ArrayList<>();
        applicationRepository.findByCandidateOrderByCreatedAtDesc(target, window)
                .forEach(application -> events.add(applicationEvent(application)));
        interviewSessionRepository.findByCandidateOrderByCreatedAtDesc(target, window)
                .forEach(session -> events.add(sessionEvent(session)));
        interviewSlotRepository.findByBookedByAndCancelledFalseOrderByStartTimeDesc(target, window)
                .forEach(slot -> events.add(slotEvent(slot)));
        events.sort(Comparator.comparing(UserActivityResponse::getOccurredAt).reversed());

        long total = applicationRepository.countByCandidate(target)
                + interviewSessionRepository.countByCandidate(target)
                + interviewSlotRepository.countByBookedByAndCancelledFalse(target);
        int from = Math.min(page * size, events.size());
        int to = Math.min(from + size, events.size());
        return new PageImpl<>(events.subList(from, to), PageRequest.of(page, size), total);
    }

    private static UserActivityResponse applicationEvent(Application application) {
        return UserActivityResponse.builder()
                .type(UserActivityResponse.ActivityType.APPLICATION)
                .id(application.getId())
                .title(application.getJob() == null ? null : application.getJob().getTitle())
                .status(application.getStatus().name())
                .occurredAt(application.getCreatedAt())
                .build();
    }

    private static UserActivityResponse sessionEvent(InterviewSession session) {
        String title = session.getTopic() != null && !session.getTopic().isBlank()
                ? session.getTopic()
                : session.getJob() == null ? "Luyện phỏng vấn" : session.getJob().getTitle();
        return UserActivityResponse.builder()
                .type(UserActivityResponse.ActivityType.MOCK_SESSION)
                .id(session.getId())
                .title(title)
                .status(session.getStatus().name())
                .occurredAt(session.getCreatedAt())
                .build();
    }

    private static UserActivityResponse slotEvent(InterviewSlot slot) {
        return UserActivityResponse.builder()
                .type(UserActivityResponse.ActivityType.INTERVIEW_SLOT)
                .id(slot.getId())
                .title(slot.getEmployer() == null ? null : slot.getEmployer().getFullName())
                .status("BOOKED")
                .occurredAt(slot.getStartTime())
                .build();
    }

    @Override
    public Page<AuditLogResponse> getAuditLogs(AuditLogQuery query) {
        Pageable pageable = PageRequest.of(query.page(), query.size(),
                Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<AdminAuditLog> logs = auditLogRepository.search(
                query.adminId(), query.targetUserId(),
                query.action() == null ? null : query.action().name(),
                query.createdFrom(), query.createdTo(), pageable);

        Map<Long, User> targets = loadTargets(logs.getContent());
        return logs.map(log -> {
            AuditLogResponse response = AuditLogResponse.from(log);
            User target = log.getTargetUserId() == null ? null : targets.get(log.getTargetUserId());
            if (target != null) {
                response.setTargetEmail(target.getEmail());
                response.setTargetFullName(target.getFullName());
            }
            return response;
        });
    }

    // ── mutations ────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public UserDetailResponse lockUser(Long targetUserId, LockAccountRequest request) {
        User admin = requireAdmin();
        String reason = normalizedReason(request == null ? null : request.getReason());
        boolean notify = request == null || request.shouldNotify();
        return attempt(admin, targetUserId, AuditAction.LOCK_USER, () -> {
            User target = findLiveUserOrThrow(targetUserId);
            if (target.isLocked()) {
                throw new BadRequestException("Tài khoản đã bị khóa");
            }
            rejectSelfAction(admin, targetUserId, "khóa");
            rejectAdminTarget(target);
            target.setLocked(true);
            target.setLockedAt(LocalDateTime.now());
            target.setTokenVersion(target.getTokenVersion() + 1);
            userRepository.save(target);
            userSessionService.revokeAll(targetUserId);
            String detail = "Khóa tài khoản: " + target.getEmail()
                    + (reason != null ? " — Lý do: " + reason : "");
            saveAuditLog(admin, targetUserId, AuditAction.LOCK_USER, detail);
            if (notify) {
                emailService.sendAccountLockedEmail(target.getEmail(), target.getFullName());
            }
            return UserDetailResponse.from(target);
        });
    }

    @Override
    public BulkUserActionResult bulkLock(BulkUserActionRequest request) {
        requireAdmin();
        LockAccountRequest lockRequest = new LockAccountRequest();
        lockRequest.setReason(request.getReason());
        lockRequest.setNotifyUser(request.shouldNotify());
        return runBulk(request.getIds(), id -> {
            LockAccountRequest perId = new LockAccountRequest();
            perId.setReason(lockRequest.getReason());
            perId.setNotifyUser(lockRequest.shouldNotify());
            return selfProvider.getObject().lockUser(id, perId);
        });
    }

    @Override
    public BulkUserActionResult bulkUnlock(BulkUserActionRequest request) {
        requireAdmin();
        return runBulk(request.getIds(), id -> selfProvider.getObject().unlockUser(id));
    }

    @Override
    public BulkUserActionResult bulkRole(BulkRoleChangeRequest request) {
        requireAdmin();
        return runBulk(request.getIds(), id -> selfProvider.getObject().changeRole(id, request.getRole()));
    }

    /** Aggregates per-id results; each id goes through the transactional proxy, so one
     *  rejected id leaves the others untouched (spec 008 D1). */
    private BulkUserActionResult runBulk(List<Long> ids, java.util.function.Function<Long, UserDetailResponse> action) {
        List<BulkUserActionResult.FailedItem> failed = new java.util.ArrayList<>();
        int succeeded = 0;
        for (Long id : ids) {
            try {
                action.apply(id);
                succeeded++;
            } catch (BadRequestException | ResourceNotFoundException e) {
                failed.add(new BulkUserActionResult.FailedItem(id, e.getMessage()));
            }
        }
        return new BulkUserActionResult(ids.size(), succeeded, failed);
    }

    private static String normalizedReason(String reason) {
        if (reason == null || reason.isBlank()) {
            return null;
        }
        String trimmed = reason.trim();
        if (trimmed.length() > 500) {
            throw new BadRequestException("Lý do tối đa 500 ký tự");
        }
        return trimmed;
    }

    /** Mockup rule "Không thể khóa Super Admin": admin accounts are never lockable. */
    private void rejectAdminTarget(User target) {
        if (target.getRole() == Role.ROLE_ADMIN) {
            throw new BadRequestException("Không thể khóa tài khoản quản trị viên");
        }
    }

    @Override
    @Transactional
    public UserDetailResponse unlockUser(Long targetUserId) {
        User admin = requireAdmin();
        return attempt(admin, targetUserId, AuditAction.UNLOCK_USER, () -> {
            User target = findLiveUserOrThrow(targetUserId);
            if (!target.isLocked()) {
                throw new BadRequestException("Tài khoản không ở trạng thái bị khóa");
            }
            target.setLocked(false);
            target.setLockedAt(null);
            userRepository.save(target);
            saveAuditLog(admin, targetUserId, AuditAction.UNLOCK_USER,
                    "Mở khóa tài khoản: " + target.getEmail());
            emailService.sendAccountUnlockedEmail(target.getEmail(), target.getFullName());
            return UserDetailResponse.from(target);
        });
    }

    @Override
    @Transactional
    public UserDetailResponse setUserEnabled(Long targetUserId, boolean enabled) {
        User admin = requireAdmin();
        AuditAction action = enabled ? AuditAction.ENABLE_USER : AuditAction.DISABLE_USER;
        return attempt(admin, targetUserId, action, () -> {
            User target = findLiveUserOrThrow(targetUserId);
            rejectSelfAction(admin, targetUserId, "thay đổi trạng thái");
            if (target.isEnabled() == enabled) {
                throw new BadRequestException(enabled
                        ? "Tài khoản đã ở trạng thái hoạt động"
                        : "Tài khoản đã ở trạng thái tắt");
            }
            if (enabled && hasPendingEmailVerification(target)) {
                throw new BadRequestException("Tài khoản chưa xác thực email. "
                        + "Yêu cầu người dùng bấm link xác thực trong email trước.");
            }
            target.setEnabled(enabled);
            if (!enabled) {
                target.setTokenVersion(target.getTokenVersion() + 1);
            }
            userRepository.save(target);
            if (!enabled) {
                userSessionService.revokeAll(targetUserId);
            }
            saveAuditLog(admin, targetUserId, action,
                    (enabled ? "Bật tài khoản: " : "Tắt tài khoản: ") + target.getEmail());
            return UserDetailResponse.from(target);
        });
    }

    @Override
    @Transactional
    public void deleteUser(Long targetUserId) {
        User admin = requireAdmin();
        attempt(admin, targetUserId, AuditAction.DELETE_USER, () -> {
            rejectSelfAction(admin, targetUserId, "xóa");
            User target = findLiveUserOrThrow(targetUserId);
            rejectIfLastAdmin(target, "xóa");
            target.setDeletedAt(LocalDateTime.now());
            target.setTokenVersion(target.getTokenVersion() + 1);
            userRepository.save(target);
            userSessionService.revokeAll(targetUserId);
            saveAuditLog(admin, targetUserId, AuditAction.DELETE_USER,
                    "Xóa tài khoản: " + target.getEmail());
            emailService.sendAccountDeletedEmail(target.getEmail(), target.getFullName());
            return null;
        });
    }

    @Override
    @Transactional
    public UserDetailResponse restoreUser(Long targetUserId) {
        User admin = requireAdmin();
        return attempt(admin, targetUserId, AuditAction.RESTORE_USER, () -> {
            User target = findUserOrThrow(targetUserId);
            if (target.getDeletedAt() == null) {
                throw new BadRequestException("Tài khoản không ở trạng thái đã xóa");
            }
            // Delete only touched deletedAt/tokenVersion, so enabled/locked/role are still the
            // pre-delete values and clearing deletedAt restores them.
            target.setDeletedAt(null);
            userRepository.save(target);
            saveAuditLog(admin, targetUserId, AuditAction.RESTORE_USER,
                    "Khôi phục tài khoản: " + target.getEmail());
            return UserDetailResponse.from(target);
        });
    }

    @Override
    @Transactional
    public void resetPassword(Long targetUserId) {
        User admin = requireAdmin();
        attempt(admin, targetUserId, AuditAction.RESET_PASSWORD, () -> {
            User target = findLiveUserOrThrow(targetUserId);
            String resetToken = UUID.randomUUID().toString();
            target.setResetToken(resetToken);
            target.setResetTokenExpiry(LocalDateTime.now().plusMinutes(RESET_TOKEN_TTL_MINUTES));
            userRepository.save(target);
            saveAuditLog(admin, targetUserId, AuditAction.RESET_PASSWORD,
                    "Gửi email reset mật khẩu: " + target.getEmail());
            // Mail failure propagates as MailDeliveryException -> 502 and the transaction rolls the
            // token back, so an existing reset link is never silently replaced by an unsent one.
            emailService.sendResetPasswordEmail(target.getEmail(), target.getFullName(), resetToken);
            return null;
        });
    }

    @Override
    @Transactional
    public UserDetailResponse changeRole(Long targetUserId, Role newRole) {
        User admin = requireAdmin();
        return attempt(admin, targetUserId, AuditAction.CHANGE_ROLE, () -> {
            rejectSelfAction(admin, targetUserId, "thay đổi vai trò");
            User target = findLiveUserOrThrow(targetUserId);
            if (target.getRole() == Role.ROLE_ADMIN && newRole != Role.ROLE_ADMIN) {
                rejectIfLastAdmin(target, "thay đổi vai trò");
            }
            String detail = String.format("Đổi role: %s → %s | user: %s",
                    target.getRole().name(), newRole.name(), target.getEmail());
            target.setRole(newRole);
            target.setTokenVersion(target.getTokenVersion() + 1);
            userRepository.save(target);
            userSessionService.revokeAll(targetUserId);
            saveAuditLog(admin, targetUserId, AuditAction.CHANGE_ROLE, detail);
            return UserDetailResponse.from(target);
        });
    }

    @Override
    @Transactional
    public UserDetailResponse disableUserTwoFactor(Long targetUserId, String reason) {
        User admin = requireAdmin();
        return attempt(admin, targetUserId, AuditAction.DISABLE_2FA, () -> {
            User target = findLiveUserOrThrow(targetUserId);
            if (!target.isTotpEnabled()) {
                throw new BadRequestException("Tài khoản chưa bật 2FA");
            }
            target.setTotpEnabled(false);
            target.setTotpSecret(null);
            userRepository.save(target);
            // Mất thiết bị thì recovery codes cũng không còn nghĩa gì — thu hồi hết.
            recoveryCodeRepository.deleteByUserId(target.getId());
            saveAuditLog(admin, targetUserId, AuditAction.DISABLE_2FA,
                    "Tắt 2FA buộc: " + target.getEmail() + " | lý do: " + reason);
            return UserDetailResponse.from(target);
        });
    }

    // ── invitations & profile edits (plan 002, Lô B) ─────────────────────────
    @Override
    @Transactional
    public UserDetailResponse inviteUser(AdminInviteUserRequest request) {
        User admin = requireAdmin();
        String email = request.getEmail().trim();
        String fullName = request.getFullName().trim();

        // Chặn lúc ghi (Constitution III): kiểm tra trước khi INSERT để message nói đúng hệ quả.
        if (userRepository.existsByEmail(email)) {
            throw new EmailAlreadyRegisteredException(emailConflictMessage(email));
        }

        String verificationToken = UUID.randomUUID().toString();
        User invited = User.builder()
                .email(email)
                // users.password is NOT NULL and the admin never types one (Clarify C-4), so the
                // column holds a random encoded value nobody knows: the account cannot be signed
                // into until the invited person sets their own password.
                .password(passwordEncoder.encode(UUID.randomUUID().toString()))
                .fullName(fullName)
                .phone(request.getPhone())
                .role(request.getRole())
                .enabled(false)
                .locked(false)
                .verificationToken(verificationToken)
                .verificationTokenExpiry(LocalDateTime.now().plusHours(INVITATION_TOKEN_TTL_HOURS))
                .build();
        try {
            userRepository.save(invited);
            // The pre-check above cannot see a second request that is in flight; flushing here
            // turns that race into a 409 from this method instead of a 500 at commit time.
            userRepository.flush();
        } catch (DataIntegrityViolationException e) {
            throw new EmailAlreadyRegisteredException(emailConflictMessage(email));
        }

        saveAuditLog(admin, invited.getId(), AuditAction.INVITE_USER, "Mời tài khoản: " + email);
        // Mail failure -> MailDeliveryException -> 502 and the transaction removes the user again.
        emailService.sendVerificationEmail(email, fullName, verificationToken);
        return UserDetailResponse.from(invited);
    }

    @Override
    @Transactional
    public UserDetailResponse resendInvitation(Long targetUserId) {
        User admin = requireAdmin();
        return attempt(admin, targetUserId, AuditAction.RESEND_INVITATION, () -> {
            User target = findLiveUserOrThrow(targetUserId);
            if (target.isEnabled()) {
                throw new BadRequestException("Tài khoản đã xác thực email");
            }
            String token = UUID.randomUUID().toString();
            target.setVerificationToken(token);
            target.setVerificationTokenExpiry(LocalDateTime.now().plusHours(INVITATION_TOKEN_TTL_HOURS));
            userRepository.save(target);
            saveAuditLog(admin, targetUserId, AuditAction.RESEND_INVITATION,
                    "Gửi lại email mời: " + target.getEmail());
            // 502 rolls the rotation back, so the member never holds a link whose replacement
            // was silently swapped by a send that failed.
            emailService.sendResendVerificationEmail(target.getEmail(), target.getFullName(), token);
            return UserDetailResponse.from(target);
        });
    }

    @Override
    @Transactional
    public UserDetailResponse revokeInvitation(Long targetUserId) {
        User admin = requireAdmin();
        return attempt(admin, targetUserId, AuditAction.REVOKE_INVITATION, () -> {
            User target = findLiveUserOrThrow(targetUserId);
            if (target.getVerificationToken() == null) {
                throw new BadRequestException("Tài khoản không có lời mời đang chờ xác thực");
            }
            // Only the link dies: enabled/locked/tokenVersion are untouched, and a later
            // "Resend" can bring the invitation back.
            target.setVerificationToken(null);
            target.setVerificationTokenExpiry(null);
            userRepository.save(target);
            saveAuditLog(admin, targetUserId, AuditAction.REVOKE_INVITATION,
                    "Thu hồi link mời: " + target.getEmail());
            return UserDetailResponse.from(target);
        });
    }

    @Override
    @Transactional
    public AdminProfileUpdateResponse updateProfile(Long targetUserId, AdminUpdateProfileRequest request) {
        User admin = requireAdmin();
        return attempt(admin, targetUserId, AuditAction.UPDATE_PROFILE, () -> {
            User target = findLiveUserOrThrow(targetUserId);
            String fullName = blankToNull(request.getFullName());
            String phone = blankToNull(request.getPhone());
            String newEmail = blankToNull(request.getEmail());
            boolean emailChanged = newEmail != null && !newEmail.equalsIgnoreCase(target.getEmail());

            if (request.getFullName() != null && fullName == null) {
                throw new BadRequestException("Họ tên không được để trống");
            }
            if (fullName == null && phone == null && newEmail == null) {
                throw new BadRequestException("Không có thông tin nào để cập nhật");
            }
            if (emailChanged && admin.getId().equals(targetUserId)) {
                // Changing your own address would disable your own session mid-request.
                throw new BadRequestException("Không thể đổi email của chính tài khoản quản trị đang đăng nhập");
            }
            if (emailChanged && userRepository.existsByEmail(newEmail)) {
                throw new EmailAlreadyRegisteredException(emailConflictMessage(newEmail));
            }

            if (fullName != null) {
                target.setFullName(fullName);
            }
            if (request.getPhone() != null) {
                target.setPhone(phone);
            }
            if (emailChanged) {
                applyEmailChange(target, newEmail);
            }
            userRepository.save(target);
            String detail = emailChanged
                    ? "Cập nhật hồ sơ, đổi email sang: " + newEmail
                    : "Cập nhật hồ sơ: " + target.getEmail();
            saveAuditLog(admin, targetUserId, AuditAction.UPDATE_PROFILE, detail);
            if (emailChanged) {
                emailService.sendVerificationEmail(newEmail, target.getFullName(),
                        target.getVerificationToken());
            }
            return new AdminProfileUpdateResponse(UserDetailResponse.from(target), emailChanged);
        });
    }

    /** A new address is an unproven one: back to pending invite, and old tokens must stop working. */
    private void applyEmailChange(User target, String newEmail) {
        target.setEmail(newEmail);
        target.setEnabled(false);
        target.setVerificationToken(UUID.randomUUID().toString());
        target.setVerificationTokenExpiry(LocalDateTime.now().plusHours(INVITATION_TOKEN_TTL_HOURS));
        target.setTokenVersion(target.getTokenVersion() + 1);
        userSessionService.revokeAll(target.getId());
    }

    // ── CSV export ───────────────────────────────────────────────────────────

    @Override
    @Transactional
    public String exportUsers(String search, UserStatusFilter status, Role role) {
        User admin = requireAdmin();
        if (status == UserStatusFilter.DELETED) {
            throw new BadRequestException("Bộ lọc DELETED không dùng cho bảng xuất này, "
                    + "hãy dùng GET /api/v1/admin/users/deleted");
        }
        Page<User> rows = userRepository.findAllWithFilters(escapeSearch(search), role,
                status == null ? null : status.name(),
                PageRequest.of(0, MAX_EXPORT_ROWS + 1, Sort.by(Sort.Direction.DESC, "createdAt")));
        if (rows.getTotalElements() > MAX_EXPORT_ROWS) {
            throw new BadRequestException("Kết quả vượt " + MAX_EXPORT_ROWS
                    + " dòng, hãy thu hẹp bộ lọc");
        }

        Map<String, LocalDateTime> lastLogins = lastLoginsFor(rows.getContent());
        StringBuilder csv = new StringBuilder();
        csv.append("id,email,fullName,role,status,pendingVerification,lastLoginAt,createdAt,deletedAt\n");
        for (User user : rows.getContent()) {
            appendCsvRow(csv, user, lastLogins.get(lowerEmail(user.getEmail())));
        }
        saveAuditLog(admin, null, AuditAction.EXPORT_USERS, "Xuất CSV " + rows.getNumberOfElements()
                + " dòng | bộ lọc: search=" + nullToEmpty(search) + ", role=" + nullToEmpty(role)
                + ", status=" + nullToEmpty(status));
        return csv.toString();
    }

    private void appendCsvRow(StringBuilder csv, User user, LocalDateTime lastLoginAt) {
        csv.append(csvCell(String.valueOf(user.getId()))).append(',')
                .append(csvCell(user.getEmail())).append(',')
                .append(csvCell(user.getFullName())).append(',')
                .append(csvCell(user.getRole() == null ? null : user.getRole().name())).append(',')
                .append(csvCell(UserSummaryResponse.from(user).getStatus())).append(',')
                .append(user.getVerificationToken() != null ? "true" : "false").append(',')
                .append(csvCell(text(lastLoginAt))).append(',')
                .append(csvCell(text(user.getCreatedAt()))).append(',')
                .append(csvCell(text(user.getDeletedAt())))
                .append('\n');
    }

    /** null timestamps are an empty cell, not the word "null". */
    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    /**
     * fullName is free text an end user typed, so it can contain commas, quotes, newlines — and a
     * leading "=" that a spreadsheet would happily execute.
     */
    private static String csvCell(String value) {
        if (value == null) {
            return "";
        }
        String cell = value;
        if (!cell.isEmpty() && "=+-@\t\r".indexOf(cell.charAt(0)) >= 0) {
            cell = "'" + cell;
        }
        if (cell.indexOf(',') >= 0 || cell.indexOf('"') >= 0 || cell.indexOf('\n') >= 0
                || cell.indexOf('\r') >= 0) {
            return '"' + cell.replace("\"", "\"\"") + '"';
        }
        return cell;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String nullToEmpty(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    /** Soft-deleted rows keep their unique email, so a plain "already exists" would dead-end the admin. */
    private String emailConflictMessage(String email) {
        Optional<User> existing = userRepository.findByEmail(email);
        if (existing.isPresent() && existing.get().getDeletedAt() != null) {
            return "Email này đang thuộc về tài khoản đã bị xóa (id " + existing.get().getId()
                    + "). Khôi phục tài khoản đó bằng POST /api/v1/admin/users/"
                    + existing.get().getId() + "/restore thay vì tạo mới.";
        }
        return "Email đã tồn tại trong hệ thống";
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    /** Runs the business operation; a rule rejection is audited in its own transaction. */
    private <T> T attempt(User admin, Long targetUserId, AuditAction action, java.util.function.Supplier<T> body) {
        try {
            return body.get();
        } catch (BadRequestException | ResourceNotFoundException e) {
            // A wrong id has no subject to audit; a rejected operation on an existing user does.
            if (userRepository.findById(targetUserId).isPresent()) {
                auditWriter.writeRejected(admin, targetUserId, action, e.getMessage());
            }
            throw e;
        }
    }

    private Pageable pageable(int page, int size) {
        return PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
    }

    /** Adds {@code lastLoginAt} to a page with ONE read of login_attempts (plan 002 R-3: no N+1). */
    private Page<UserSummaryResponse> withLastLogin(Page<User> users) {
        Map<String, LocalDateTime> lastLogins = lastLoginsFor(users.getContent());
        return users.map(user -> {
            UserSummaryResponse response = UserSummaryResponse.from(user);
            response.setLastLoginAt(lastLogins.get(lowerEmail(user.getEmail())));
            return response;
        });
    }

    private Map<String, LocalDateTime> lastLoginsFor(List<User> users) {
        List<String> emails = users.stream().map(User::getEmail).distinct().toList();
        if (emails.isEmpty()) {
            return Map.of();
        }
        Map<String, LocalDateTime> map = new HashMap<>();
        for (Object[] row : loginAttemptRepository.findLastSuccessByEmailIn(emails)) {
            map.put(lowerEmail((String) row[0]), (LocalDateTime) row[1]);
        }
        return map;
    }

    /** login_attempts and users can hold the same address in a different case; match on lowercase. */
    private static String lowerEmail(String email) {
        return email == null ? null : email.toLowerCase();
    }

    private static long asCount(Object value) {
        return value instanceof Number number ? number.longValue() : 0L;
    }

    /** COUNT(*) over zero rows still gives one row, but a tuple is truncated — read defensively. */
    private static Object at(Object[] row, int index) {
        return index < row.length ? row[index] : null;
    }

    private User findUserOrThrow(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng với id: " + userId));
    }

    /** Same 404 contract for every mutating admin action. */
    private User findLiveUserOrThrow(Long userId) {
        User user = findUserOrThrow(userId);
        if (user.getDeletedAt() != null) {
            throw new ResourceNotFoundException(DELETED_MESSAGE);
        }
        return user;
    }

    private void rejectSelfAction(User admin, Long targetUserId, String verb) {
        if (admin.getId().equals(targetUserId)) {
            throw new BadRequestException("Không thể " + verb + " tài khoản của chính mình");
        }
    }

    private void rejectIfLastAdmin(User target, String verb) {
        if (target.getRole() != Role.ROLE_ADMIN) {
            return;
        }
        long liveAdmins = userRepository.countByRoleAndDeletedAtIsNull(Role.ROLE_ADMIN);
        if (liveAdmins <= 1) {
            throw new BadRequestException("Không thể " + verb
                    + ": phải luôn có ít nhất một tài khoản Quản trị viên trong hệ thống");
        }
    }

    private boolean hasPendingEmailVerification(User user) {
        return user.getVerificationToken() != null;
    }

    /** Escapes LIKE wildcards so a search string cannot match the whole table. */
    private String escapeSearch(String search) {
        if (search == null || search.isBlank()) {
            return null;
        }
        String trimmed = search.trim();
        if (trimmed.length() > MAX_SEARCH_LENGTH) {
            throw new BadRequestException("Từ khóa tìm kiếm tối đa " + MAX_SEARCH_LENGTH + " ký tự");
        }
        return trimmed.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private Map<Long, User> loadTargets(List<AdminAuditLog> logs) {
        List<Long> ids = logs.stream()
                .map(AdminAuditLog::getTargetUserId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return userRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
    }

    /**
     * Resolve the currently authenticated admin. Never falls back to a placeholder: an audit row
     * with a fake admin is worse than refusing the action.
     */
    private User requireAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new AccessDeniedException("Không xác định được tài khoản quản trị");
        }
        Object principal = auth.getPrincipal();
        if (principal instanceof User u) {
            return u;
        }
        String email = null;
        if (principal instanceof UserDetails ud) {
            email = ud.getUsername();
        } else if (principal instanceof String s) {
            email = s;
        }
        if (email != null) {
            Optional<User> opt = userRepository.findByEmail(email);
            if (opt.isPresent()) {
                return opt.get();
            }
        }
        throw new AccessDeniedException("Không xác định được tài khoản quản trị");
    }

    private void saveAuditLog(User admin, Long targetUserId, AuditAction action, String detail) {
        auditLogRepository.save(AdminAuditLog.builder()
                .adminId(admin.getId())
                .adminEmail(admin.getEmail())
                .targetUserId(targetUserId)
                .action(action.name())
                .detail(detail)
                .succeeded(true)
                .build());
    }
}
