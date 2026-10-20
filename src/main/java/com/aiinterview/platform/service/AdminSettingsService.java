package com.aiinterview.platform.service;

import com.aiinterview.platform.model.dto.response.AdminSettingsResponse;

import java.util.Map;

public interface AdminSettingsService {

    AdminSettingsResponse getSettings();

    /**
     * Validates every provided key against {@link com.aiinterview.platform.model.enums.SettingDefinition};
     * one bad key rejects the whole body with 422 (nothing is written). No keys provided
     * (empty body) is a no-op that changes nothing.
     */
    AdminSettingsResponse updateSettings(Map<String, Object> body);
}
