package com.aiinterview.platform.service.impl;

import com.aiinterview.platform.model.entity.Application;
import com.aiinterview.platform.model.entity.InterviewSlot;
import com.aiinterview.platform.model.entity.JobDescription;
import com.aiinterview.platform.model.entity.User;
import com.aiinterview.platform.model.enums.ApplicationStatus;
import com.aiinterview.platform.model.repository.ApplicationRepository;
import com.aiinterview.platform.model.repository.InterviewSlotRepository;
import com.aiinterview.platform.service.EmailService;
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
        lead(60, 15);
        when(slotRepository.findByBookedByIsNotNullAndCancelledFalseAndStartTimeGreaterThanEqualAndStartTimeLessThan(
                any(), any())).thenReturn(List.of());

        LocalDateTime startedAt = LocalDateTime.now();
        reminderService.remindUpcomingInterviews();

        ArgumentCaptor<LocalDateTime> from = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> to = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(slotRepository).findByBookedByIsNotNullAndCancelledFalseAndStartTimeGreaterThanEqualAndStartTimeLessThan(
                from.capture(), to.capture());

        assertEquals(15L, Duration.between(from.getValue(), to.getValue()).toMinutes());
        // [now+lead-window, now+lead): biên dưới không sớm hơn now+45, biên trên không muộn hơn now+60.
        assertTrue(!from.getValue().isBefore(startedAt.plusMinutes(45)), "cửa sổ phải bắt đầu từ now+45");
        assertTrue(to.getValue().isAfter(startedAt.plusMinutes(55)), "cửa sổ phải kết thúc quanh now+60");
        // Không có buổi nào thì đừng chạm repository đơn.
        verifyNoInteractions(applicationRepository);
    }

    @Test
    @DisplayName("REM-2: mỗi buổi đã chốt gửi đúng một email cho ứng viên và một cho người phỏng vấn")
    void sendsOneEmailToEachSideOfAScheduledBooking() {
        lead(60, 15);
        InterviewSlot slot = slot(LocalDateTime.now().plusMinutes(50));
        Application application = scheduledApplication(slot);
        when(slotRepository.findByBookedByIsNotNullAndCancelledFalseAndStartTimeGreaterThanEqualAndStartTimeLessThan(
                any(), any())).thenReturn(List.of(slot));
        when(applicationRepository.findBySlotIdInAndStatus(anyCollection(), eq(ApplicationStatus.SCHEDULED)))
                .thenReturn(List.of(application));

        reminderService.remindUpcomingInterviews();

        verify(emailService).sendInterviewReminderEmail(eq("candidate@example.com"), eq("Candidate One"),
                eq("Backend Java Developer"), anyString(), eq("Ứng viên"));
        verify(emailService).sendInterviewReminderEmail(eq("employer@example.com"), eq("Employer One"),
                eq("Backend Java Developer"), anyString(), eq("Người phỏng vấn"));
    }

    @Test
    @DisplayName("REM-3: slot còn mang dấu của đơn đã rời vòng hẹn lịch thì im lặng, không nhắc ma")
    void skipsSlotWhoseApplicationLeftTheScheduledStage() {
        lead(60, 15);
        InterviewSlot slot = slot(LocalDateTime.now().plusMinutes(50));
        when(slotRepository.findByBookedByIsNotNullAndCancelledFalseAndStartTimeGreaterThanEqualAndStartTimeLessThan(
                any(), any())).thenReturn(List.of(slot));
        when(applicationRepository.findBySlotIdInAndStatus(anyCollection(), eq(ApplicationStatus.SCHEDULED)))
                .thenReturn(List.of());

        reminderService.remindUpcomingInterviews();

        verifyNoInteractions(emailService);
    }

    private void lead(long leadMinutes, long windowMinutes) {
        ReflectionTestUtils.setField(reminderService, "leadMinutes", leadMinutes);
        ReflectionTestUtils.setField(reminderService, "windowMinutes", windowMinutes);
    }

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
