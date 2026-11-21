package com.aiinterview.platform.model.enums;

public enum InterviewPlan {
    FREE(5, 3),
    NORMAL(15, 5),
    ADVANCED(20, 10);

    private final int maxQuestionsPerSession;
    private final int sessionsPerDay;

    InterviewPlan(int maxQuestionsPerSession, int sessionsPerDay) {
        this.maxQuestionsPerSession = maxQuestionsPerSession;
        this.sessionsPerDay = sessionsPerDay;
    }

    public int getMaxQuestionsPerSession() {
        return maxQuestionsPerSession;
    }

    public int getSessionsPerDay() {
        return sessionsPerDay;
    }
}