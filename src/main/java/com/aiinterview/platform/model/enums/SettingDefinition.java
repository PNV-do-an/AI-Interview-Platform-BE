package com.aiinterview.platform.model.enums;

/** Catalog of every platform setting: type, allowed values, default (spec 007 FR-002). */
public enum SettingDefinition {

    GENERAL_PLATFORM_NAME("general.platformName", SettingType.STRING, "AI Interview Platform"),
    GENERAL_SUPPORT_EMAIL("general.supportEmail", SettingType.EMAIL, ""),
    GENERAL_DEFAULT_LANGUAGE("general.defaultLanguage", SettingType.ENUM, "vi-VN",
            "vi-VN", "en-US", "ja-JP"),
    GENERAL_TIMEZONE("general.timezone", SettingType.ENUM, "GMT+7",
            "GMT+7", "GMT+8", "GMT+9", "GMT+0"),

    NOTIFICATION_IN_APP("notification.inApp", SettingType.BOOL, "true"),
    NOTIFICATION_INTERVIEW_REMINDER_EMAIL("notification.interviewReminderEmail", SettingType.BOOL, "true"),
    NOTIFICATION_JD_DECISION_EMAIL("notification.jdDecisionEmail", SettingType.BOOL, "true"),
    NOTIFICATION_WEEKLY_DIGEST_EMAIL("notification.weeklyDigestEmail", SettingType.BOOL, "false"),

    AI_INTERVIEW_ENABLED("ai.interviewEnabled", SettingType.BOOL, "true"),
    AI_DEFAULT_QUESTION_COUNT("ai.defaultQuestionCount", SettingType.INT, "5"),
    AI_MAX_ANSWER_SECONDS("ai.maxAnswerSeconds", SettingType.ENUM, "180",
            "120", "180", "300"),
    AI_EVALUATOR_MODEL("ai.evaluatorModel", SettingType.ENUM, "SMART_EVALUATOR_V2_5",
            "SMART_EVALUATOR_V2_5", "DEEPSEEK_FAST_ENGINE", "GPT4O_ENTERPRISE"),
    AI_STT_ENABLED("ai.sttEnabled", SettingType.BOOL, "true"),
    AI_PROMPT_CONFIG("ai.promptConfig", SettingType.TEXT, "");

    public enum SettingType {
        STRING, TEXT, EMAIL, BOOL, INT, ENUM
    }

    private final String key;
    private final SettingType type;
    private final String defaultValue;
    private final String[] allowedValues;

    SettingDefinition(String key, SettingType type, String defaultValue) {
        this(key, type, defaultValue, new String[0]);
    }

    SettingDefinition(String key, SettingType type, String defaultValue, String... allowedValues) {
        this.key = key;
        this.type = type;
        this.defaultValue = defaultValue;
        this.allowedValues = allowedValues;
    }

    public String getKey() {
        return key;
    }

    public SettingType getType() {
        return type;
    }

    public String getDefaultValue() {
        return defaultValue;
    }

    public String[] getAllowedValues() {
        return allowedValues;
    }
}
