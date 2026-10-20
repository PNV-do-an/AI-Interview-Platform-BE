package com.aiinterview.platform.service.impl;

import com.aiinterview.platform.common.exception.SettingsValidationException;
import com.aiinterview.platform.model.dto.response.AdminSettingsResponse;
import com.aiinterview.platform.model.entity.AdminAuditLog;
import com.aiinterview.platform.model.entity.SystemSetting;
import com.aiinterview.platform.model.entity.User;
import com.aiinterview.platform.model.enums.AuditAction;
import com.aiinterview.platform.model.enums.SettingDefinition;
import com.aiinterview.platform.model.repository.AdminAuditLogRepository;
import com.aiinterview.platform.model.repository.SystemSettingRepository;
import com.aiinterview.platform.model.repository.UserRepository;
import com.aiinterview.platform.security.utils.SecurityUtils;
import com.aiinterview.platform.service.AdminSettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminSettingsServiceImpl implements AdminSettingsService {

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final int MAX_PLATFORM_NAME_LENGTH = 100;
    private static final int MAX_PROMPT_LENGTH = 2000;

    private final SystemSettingRepository settingRepository;
    private final AdminAuditLogRepository auditLogRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public AdminSettingsResponse getSettings() {
        Map<String, String> stored = loadStoredValues();
        AdminSettingsResponse.Meta meta = loadMeta();
        return AdminSettingsResponse.builder()
                .general(AdminSettingsResponse.General.builder()
                        .platformName(text(stored, SettingDefinition.GENERAL_PLATFORM_NAME))
                        .supportEmail(text(stored, SettingDefinition.GENERAL_SUPPORT_EMAIL))
                        .defaultLanguage(text(stored, SettingDefinition.GENERAL_DEFAULT_LANGUAGE))
                        .timezone(text(stored, SettingDefinition.GENERAL_TIMEZONE))
                        .build())
                .notification(AdminSettingsResponse.Notification.builder()
                        .inApp(bool(stored, SettingDefinition.NOTIFICATION_IN_APP))
                        .interviewReminderEmail(bool(stored, SettingDefinition.NOTIFICATION_INTERVIEW_REMINDER_EMAIL))
                        .jdDecisionEmail(bool(stored, SettingDefinition.NOTIFICATION_JD_DECISION_EMAIL))
                        .weeklyDigestEmail(bool(stored, SettingDefinition.NOTIFICATION_WEEKLY_DIGEST_EMAIL))
                        .build())
                .ai(AdminSettingsResponse.Ai.builder()
                        .interviewEnabled(bool(stored, SettingDefinition.AI_INTERVIEW_ENABLED))
                        .defaultQuestionCount(intValue(stored, SettingDefinition.AI_DEFAULT_QUESTION_COUNT))
                        .maxAnswerSeconds(intValue(stored, SettingDefinition.AI_MAX_ANSWER_SECONDS))
                        .evaluatorModel(text(stored, SettingDefinition.AI_EVALUATOR_MODEL))
                        .sttEnabled(bool(stored, SettingDefinition.AI_STT_ENABLED))
                        .promptConfig(text(stored, SettingDefinition.AI_PROMPT_CONFIG))
                        .build())
                .meta(meta)
                .build();
    }

    @Override
    @Transactional
    public AdminSettingsResponse updateSettings(Map<String, Object> body) {
        User admin = SecurityUtils.getCurrentUser(userRepository);
        Map<String, String> violations = new LinkedHashMap<>();
        Map<String, String> toWrite = new LinkedHashMap<>();

        for (Map.Entry<String, Object> groupEntry : body.entrySet()) {
            String group = groupEntry.getKey();
            if (!(groupEntry.getValue() instanceof Map<?, ?> fields)) {
                violations.put(group, "Nhóm cấu hình phải là object");
                continue;
            }
            for (Map.Entry<?, ?> field : ((Map<?, Object>) fields).entrySet()) {
                String flatKey = group + "." + field.getKey();
                SettingDefinition definition = definitionByKey(flatKey);
                if (definition == null) {
                    violations.put(flatKey, "Key cấu hình không tồn tại");
                    continue;
                }
                validateAndSerialize(definition, field.getValue(), violations, toWrite);
            }
        }
        if (!violations.isEmpty()) {
            throw new SettingsValidationException(violations);
        }
        if (!toWrite.isEmpty()) {
            for (Map.Entry<String, String> entry : toWrite.entrySet()) {
                SystemSetting row = settingRepository.findBySettingKey(entry.getKey())
                        .orElseGet(() -> SystemSetting.builder().settingKey(entry.getKey()).build());
                row.setSettingValue(entry.getValue());
                row.setUpdatedBy(admin.getEmail());
                settingRepository.save(row);
            }
            auditLogRepository.save(AdminAuditLog.builder()
                    .adminId(admin.getId())
                    .adminEmail(admin.getEmail())
                    .action(AuditAction.UPDATE_SETTINGS.name())
                    .detail("Cập nhật cấu hình: " + String.join(", ", toWrite.keySet()))
                    .succeeded(true)
                    .build());
        }
        return getSettings();
    }

    // ── validation ───────────────────────────────────────────────────────────

    private void validateAndSerialize(SettingDefinition definition, Object value,
                                      Map<String, String> violations, Map<String, String> toWrite) {
        String key = definition.getKey();
        switch (definition.getType()) {
            case STRING -> {
                String s = value == null ? null : String.valueOf(value);
                if (s == null || s.isBlank()) {
                    violations.put(key, "Giá trị không được để trống");
                } else if (s.trim().length() > MAX_PLATFORM_NAME_LENGTH) {
                    violations.put(key, "Giá trị tối đa " + MAX_PLATFORM_NAME_LENGTH + " ký tự");
                } else {
                    toWrite.put(key, s.trim());
                }
            }
            case TEXT -> {
                String s = value == null ? "" : String.valueOf(value);
                if (s.length() > MAX_PROMPT_LENGTH) {
                    violations.put(key, "Giá trị tối đa " + MAX_PROMPT_LENGTH + " ký tự");
                } else {
                    toWrite.put(key, s);
                }
            }
            case EMAIL -> {
                String s = value == null ? "" : String.valueOf(value).trim();
                if (!s.isEmpty() && !EMAIL_PATTERN.matcher(s).matches()) {
                    violations.put(key, "Giá trị phải là địa chỉ email hợp lệ");
                } else {
                    toWrite.put(key, s);
                }
            }
            case BOOL -> {
                if (!(value instanceof Boolean b)) {
                    violations.put(key, "Giá trị phải là true hoặc false");
                } else {
                    toWrite.put(key, String.valueOf(b));
                }
            }
            case INT -> {
                if (!(value instanceof Number n)) {
                    violations.put(key, "Giá trị phải là số");
                    break;
                }
                if (key.equals(SettingDefinition.AI_DEFAULT_QUESTION_COUNT.getKey())
                        && (n.intValue() < 3 || n.intValue() > 15)) {
                    violations.put(key, "Giá trị phải từ 3 đến 15");
                } else {
                    toWrite.put(key, String.valueOf(n.intValue()));
                }
            }
            case ENUM -> {
                String s = value == null ? null : String.valueOf(value);
                List<String> allowed = List.of(definition.getAllowedValues());
                if (s == null || !allowed.contains(s)) {
                    violations.put(key, "Giá trị được phép: " + String.join(", ", allowed));
                } else {
                    toWrite.put(key, s);
                }
            }
        }
    }

    // ── read helpers ─────────────────────────────────────────────────────────

    private Map<String, String> loadStoredValues() {
        List<String> keys = new ArrayList<>();
        for (SettingDefinition definition : SettingDefinition.values()) {
            keys.add(definition.getKey());
        }
        return settingRepository.findBySettingKeyIn(keys).stream()
                .collect(Collectors.toMap(SystemSetting::getSettingKey,
                        row -> row.getSettingValue() == null ? "" : row.getSettingValue()));
    }

    private AdminSettingsResponse.Meta loadMeta() {
        LocalDateTime latest = null;
        String author = null;
        for (SystemSetting row : settingRepository.findAll()) {
            if (row.getUpdatedAt() != null && (latest == null || row.getUpdatedAt().isAfter(latest))) {
                latest = row.getUpdatedAt();
                author = row.getUpdatedBy();
            }
        }
        return AdminSettingsResponse.Meta.builder().lastUpdatedAt(latest).lastUpdatedBy(author).build();
    }

    private static SettingDefinition definitionByKey(String flatKey) {
        for (SettingDefinition definition : SettingDefinition.values()) {
            if (definition.getKey().equals(flatKey)) {
                return definition;
            }
        }
        return null;
    }

    private static String text(Map<String, String> stored, SettingDefinition definition) {
        return stored.getOrDefault(definition.getKey(), definition.getDefaultValue());
    }

    private static boolean bool(Map<String, String> stored, SettingDefinition definition) {
        return Boolean.parseBoolean(text(stored, definition));
    }

    private static int intValue(Map<String, String> stored, SettingDefinition definition) {
        try {
            return Integer.parseInt(text(stored, definition));
        } catch (NumberFormatException e) {
            return Integer.parseInt(definition.getDefaultValue());
        }
    }
}
