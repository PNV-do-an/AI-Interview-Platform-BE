package com.aiinterview.platform.model.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.List;
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
    /** 12-month series added by 009 FR-001; the 005 groups above keep their contract. */
    private Trends trends;

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
        /** Null when no session has both timestamps (009 FR-001). */
        private Long avgDurationSeconds;
        /** Sessions still IN_PROGRESS at call time. */
        private long liveNow;
    }

    @Data
    @Builder
    public static class Trends {
        /** Exactly 12 buckets, oldest first, ending at the current month; gaps filled with 0. */
        private List<Month> months;
    }

    @Data
    @Builder
    public static class Month {
        /** "yyyy-MM". */
        private String month;
        /** interview_sessions created in that month (all modes). */
        private long mock;
        /** interview_slots booked (not cancelled) starting in that month. */
        private long formal;
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
