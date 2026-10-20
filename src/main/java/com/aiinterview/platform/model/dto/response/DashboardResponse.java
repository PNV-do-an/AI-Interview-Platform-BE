package com.aiinterview.platform.model.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

/** GET /api/v1/admin/dashboard — every number is one DB count (spec 005 FR-002). */
@Data
@Builder
public class DashboardResponse {

    private int rangeDays;

    private Users users;
    private Jobs jobs;
    private Interviews interviews;
    private Logins logins;
    private Moderation moderation;

    @Data
    @Builder
    public static class Users {
        private long total;
        private long active;
        private long pendingVerification;
        private long locked;
        private long deleted;
        /** Always contains all four Role keys, 0 included. */
        private Map<String, Long> byRole;
        private long newInPeriod;
        /** Distinct emails with a successful login in the period (002 C-1 semantics). */
        private long loginsInPeriod;
    }

    @Data
    @Builder
    public static class Jobs {
        private long total;
        /** Always contains all four JobStatus keys, 0 included. */
        private Map<String, Long> byStatus;
        private long pendingModeration;
        private long newInPeriod;
    }

    @Data
    @Builder
    public static class Interviews {
        private long total;
        private long inPeriod;
        private long completedInPeriod;
        /** Null when no scored session exists yet. */
        private Double avgOverallScore;
    }

    @Data
    @Builder
    public static class Logins {
        private long successInPeriod;
        private long failedInPeriod;
    }

    @Data
    @Builder
    public static class Moderation {
        private long pendingApproval;
    }
}
