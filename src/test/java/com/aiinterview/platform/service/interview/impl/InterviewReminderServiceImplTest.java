package com.aiinterview.platform.service.interview.impl;

import com.aiinterview.platform.model.entity.Application;
import com.aiinterview.platform.model.entity.InterviewSlot;
import com.aiinterview.platform.model.entity.JobDescription;
import com.aiinterview.platform.model.entity.User;
import com.aiinterview.platform.model.enums.ApplicationStatus;
import com.aiinterview.platform.model.repository.ApplicationRepository;
import com.aiinterview.platform.model.repository.InterviewSlotRepository;
import com.aiinterview.platform.service.infra.EmailService;
import com.aiinterview.platform.service.interview.impl.InterviewReminderServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * FR-SCH-03: job nhắc lịch chạy ngoài luồng HTTP nên cửa sổ quét và số email phải chốt ở tầng service.
 * Không có bảng "đã nhắc", vì vậy đúng một lần chạy được phép bắt kịp một buổi.
 *
 * <p>Mỗi test chia 3 khối comment: <b>Arrange</b> → <b>Act</b> → <b>Assert</b>.</p>
 */
@ExtendWith(MockitoExtension.class)
class InterviewReminderServiceImplTest {

    private static final long SLOT_ID = 7L;

    @Mock
    private InterviewSlotRepository slotRepository;
    @Mock
    private ApplicationRepository applicationRepository;
    @Mock
    private EmailService emailService;

    @InjectMocks
    private InterviewReminderServiceImpl reminderService;

    @Test
    @DisplayName("REM-1: cửa sổ quét rộng đúng bằng nhịp cron và nằm trước giờ hẹn đúng lead")
    void reminderWindowIsOnePollWideAheadOfLeadTime() {
        // Arrange: nhắc trước 60 phút, cửa sổ 15 phút (đúng nhịp cron); chưa có buổi nào
        lead(60, 15);
        when(slotRepository.findByBookedByIsNotNullAndCancelledFalseAndStartTimeGreaterThanEqualAndStartTimeLessThan(
                any(), any())).thenReturn(List.of());

        // Act: ghi lại mốc bắt đầu để so với cửa sổ mà job vừa tính
        LocalDateTime startedAt = LocalDateTime.now();
        reminderService.remindUpcomingInterviews();

        // Assert: bắt lấy cặp tham số thật sự được truyền xuống repository
        ArgumentCaptor<LocalDateTime> from = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> to = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(slotRepository).findByBookedByIsNotNullAndCancelledFalseAndStartTimeGreaterThanEqualAndStartTimeLessThan(
                from.capture(), to.capture());

        // Assert: cửa sổ rộng đúng 15 phút, nằm trong [now+lead-window, now+lead)
        assertEquals(15L, Duration.between(from.getValue(), to.getValue()).toMinutes());
        assertTrue(!from.getValue().isBefore(startedAt.plusMinutes(45)), "cửa sổ phải bắt đầu từ now+45");
        assertTrue(to.getValue().isAfter(startedAt.plusMinutes(55)), "cửa sổ phải kết thúc quanh now+60");

        // Assert: không có buổi nào thì đừng chạm repository đơn ứng tuyển
        verifyNoInteractions(applicationRepository);
    }

    @Test
    @DisplayName("REM-2: mỗi buổi đã chốt gửi đúng một email cho ứng viên và một cho người phỏng vấn")
    void sendsOneEmailToEachSideOfAScheduledBooking() {
        // Arrange: một slot đã được book trong cửa sổ, có application ở trạng thái SCHEDULED
        lead(60, 15);
        InterviewSlot slot = slot(LocalDateTime.now().plusMinutes(50));
        Application application = scheduledApplication(slot);
        when(slotRepository.findByBookedByIsNotNullAndCancelledFalseAndStartTimeGreaterThanEqualAndStartTimeLessThan(
                any(), any())).thenReturn(List.of(slot));
        when(applicationRepository.findBySlotIdInAndStatus(anyCollection(), eq(ApplicationStatus.SCHEDULED)))
                .thenReturn(List.of(application));

        // Act
        reminderService.remindUpcomingInterviews();

        // Assert: 2 email, đúng người nhận + đúng vai trò trong nội dung (không gửi cho employer 2 lần)
        verify(emailService).sendInterviewReminderEmail(eq("candidate@example.com"), eq("Candidate One"),
                eq("Backend Java Developer"), anyString(), eq("Ứng viên"));
        verify(emailService).sendInterviewReminderEmail(eq("employer@example.com"), eq("Employer One"),
                eq("Backend Java Developer"), anyString(), eq("Người phỏng vấn"));
    }

    @Test
    @DisplayName("REM-3: slot còn mang dấu của đơn đã rời vòng hẹn lịch thì im lặng, không nhắc ma")
    void skipsSlotWhoseApplicationLeftTheScheduledStage() {
        // Arrange: slot còn tồn tại nhưng application đã rời SCHEDULED (bị huỷ / đổi lịch)
        lead(60, 15);
        InterviewSlot slot = slot(LocalDateTime.now().plusMinutes(50));
        when(slotRepository.findByBookedByIsNotNullAndCancelledFalseAndStartTimeGreaterThanEqualAndStartTimeLessThan(
                any(), any())).thenReturn(List.of(slot));
        when(applicationRepository.findBySlotIdInAndStatus(anyCollection(), eq(ApplicationStatus.SCHEDULED)))
                .thenReturn(List.of());

        // Act
        reminderService.remindUpcomingInterviews();

        // Assert: không gửi email nào — nhắc một buổi đã huỷ sẽ làm ứng viên hoang mang
        verifyNoInteractions(emailService);
    }

    /** leadMinutes / windowMinutes là @Value nên phải tiêm tay trước mỗi lần chạy job. */
    private void lead(long leadMinutes, long windowMinutes) {
        ReflectionTestUtils.setField(reminderService, "leadMinutes", leadMinutes);
        ReflectionTestUtils.setField(reminderService, "windowMinutes", windowMinutes);
    }

    /** Slot đã book: có ứng viên (bookedBy) và người phỏng vấn (employer). */
    private InterviewSlot slot(LocalDateTime startTime) {
        User employer = user(2L, "employer@example.com", "Employer One");
        return InterviewSlot.builder()
                .id(SLOT_ID)
                .employer(employer)
                .startTime(startTime)
                .endTime(startTime.plusHours(1))
                .bookedBy(user(1L, "candidate@example.com", "Candidate One"))
                .build();
    }

    /** Application đang ở SCHEDULED, gắn đúng slot + job để email lấy được tên vị trí. */
    private Application scheduledApplication(InterviewSlot slot) {
        JobDescription job = JobDescription.builder()
                .employer(slot.getEmployer())
                .title("Backend Java Developer")
                .companyName("ACME")
                .build();
        return Application.builder()
                .id(99L)
                .candidate(slot.getBookedBy())
                .job(job)
                .slot(slot)
                .status(ApplicationStatus.SCHEDULED)
                .build();
    }

    private User user(Long id, String email, String fullName) {
        User user = new User();
        user.setId(id);
        user.setEmail(email);
        user.setFullName(fullName);
        return user;
    }
}
