package com.aiinterview.platform.common.exception;

import lombok.Getter;

import java.util.Map;

/** 422: the settings body parsed fine but violates a value rule (spec 007 D2). */
@Getter
public class SettingsValidationException extends RuntimeException {

    private final Map<String, String> violations;

    public SettingsValidationException(Map<String, String> violations) {
        super("Cấu hình không hợp lệ");
        this.violations = violations;
    }
}
