package com.aiinterview.platform.service.impl;

import com.aiinterview.platform.model.entity.Application;
import com.aiinterview.platform.model.entity.InterviewSlot;
import com.aiinterview.platform.model.enums.ApplicationStatus;
import com.aiinterview.platform.model.repository.ApplicationRepository;
import com.aiinterview.platform.model.repository.InterviewSlotRepository;
import com.aiinterview.platform.service.EmailService;
import com.aiinterview.platform.service.InterviewReminderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * FR-SCH-03: job định kỳ đầu tiên của repo — nhắc buổi phỏng vấn sắp diễn ra cho cả hai phía.
 * Tắt theo mặc định (app.interview-reminder.enabled=false) vì email nó gửi là email thật.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "app.interview-reminder.enabled", havingValue = "true")
public class InterviewReminderServiceImpl implements InterviewReminderService {

    private final InterviewSlotRepository slotRepository;
    private final ApplicationRepository applicationRepository;
    private final EmailService emailService;

    @Value("${app.interview-reminder.lead-minutes:60}")
    private long leadMinutes;

    /** Phải đúng bằng nhịp cron bên dưới, nếu không sẽ gửi trùng hoặc gửi sót. */
    @Value("${app.interview-reminder.window-minutes:15}")
    private long windowMinutes;

    @Override
    @Scheduled(cron = "${app.interview-reminder.cron:0 */15 * * * *}")
    @Transactional
    public void remindUpcomingInterviews() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime from = now.plusMinutes(leadMinutes).minusMinutes(windowMinutes);
        LocalDateTime to = now.plusMinutes(leadMinutes);

        List<InterviewSlot> slots = slotRepository
                .findByBookedByIsNotNullAndCancelledFalseAndStartTimeGreaterThanEqualAndStartTimeLessThan(from, to);
        if (slots.isEmpty()) {
            return;
        }

        Map<Long, Application> scheduledBySlot = applicationRepository.findBySlotIdInAndStatus(
                        slots.stream().map(InterviewSlot::getId).collect(Collectors.toList()),
                        ApplicationStatus.SCHEDULED)
                .stream()
                .collect(Collectors.toMap(a -> a.getSlot().getId(), a -> a, (first, second) -> first));

        for (InterviewSlot slot : slots) {
            // Đơn đã bị hủy/từ chối vẫn để lại dấu trên slot cho tới khi vòng khác ghi đè: không nhắc.
            Application application = scheduledBySlot.get(slot.getId());
            if (application == null) {
                continue;
            }
            String timeLabel = slot.getStartTime().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"));
            emailService.sendInterviewReminderEmail(application.getCandidate().getEmail(),
                    application.getCandidate().getFullName(), application.getJob().getTitle(), timeLabel, "Ứng viên");
            emailService.sendInterviewReminderEmail(slot.getEmployer().getEmail(),
                    slot.getEmployer().getFullName(), application.getJob().getTitle(), timeLabel, "Người phỏng vấn");
            log.info("Đã nhắc lịch phỏng vấn applicationId={} slotId={} lúc {}",
                    application.getId(), slot.getId(), timeLabel);
        }
    }
}
