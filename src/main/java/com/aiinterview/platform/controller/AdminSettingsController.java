package com.aiinterview.platform.controller;

import com.aiinterview.platform.common.response.ApiResponse;
import com.aiinterview.platform.model.dto.response.AdminSettingsResponse;
import com.aiinterview.platform.service.AdminSettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Platform settings (spec 007). A raw map body on PUT so unknown keys can be rejected
 * with a per-key 422 instead of being silently dropped by a typed DTO.
 */
@RestController
@RequestMapping("/api/v1/admin/settings")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminSettingsController {

    private final AdminSettingsService adminSettingsService;

    /** GET /api/v1/admin/settings */
    @GetMapping
    public ResponseEntity<ApiResponse<AdminSettingsResponse>> getSettings() {
        return ResponseEntity.ok(ApiResponse.success(adminSettingsService.getSettings()));
    }

    /** PUT /api/v1/admin/settings — grouped object; invalid values ⇒ 422, nothing written. */
    @PutMapping
    public ResponseEntity<ApiResponse<AdminSettingsResponse>> updateSettings(
            @RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(ApiResponse.success("Đã lưu cấu hình",
                adminSettingsService.updateSettings(body)));
    }
}
