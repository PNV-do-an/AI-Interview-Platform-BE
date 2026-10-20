package com.aiinterview.platform.model.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * GET /api/v1/admin/settings payload — grouped, values already typed from their stored
 * strings (spec 007 FR-003).
 */
@Data
@Builder
public class AdminSettingsResponse {

    private General general;
    private Notification notification;
    private Ai ai;
    private Meta meta;

    @Data
    @Builder
    public static class General {
        private String platformName;
        private String supportEmail;
        private String defaultLanguage;
        private String timezone;
    }

    @Data
    @Builder
    public static class Notification {
        private boolean inApp;
        private boolean interviewReminderEmail;
        private boolean jdDecisionEmail;
        private boolean weeklyDigestEmail;
    }

    @Data
    @Builder
    public static class Ai {
        private boolean interviewEnabled;
        private int defaultQuestionCount;
        private int maxAnswerSeconds;
        private String evaluatorModel;
        private boolean sttEnabled;
        private String promptConfig;
    }

    @Data
    @Builder
    public static class Meta {
        private LocalDateTime lastUpdatedAt;
        private String lastUpdatedBy;
    }
}
