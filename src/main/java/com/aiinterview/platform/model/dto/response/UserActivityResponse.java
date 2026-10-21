package com.aiinterview.platform.model.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/** One row of the "Hoạt động" tab: a merged event from applications / mock sessions / slots. */
@Data
@Builder
public class UserActivityResponse {

    private ActivityType type;
    private Long id;
    /** Job title, practice topic, or the company whose slot was booked. */
    private String title;
    /** Source status verbatim (PENDING, COMPLETED, SCHEDULED …) so FE can label it. */
    private String status;
    private LocalDateTime occurredAt;

    public enum ActivityType {
        APPLICATION,
        MOCK_SESSION,
        INTERVIEW_SLOT
    }
}
