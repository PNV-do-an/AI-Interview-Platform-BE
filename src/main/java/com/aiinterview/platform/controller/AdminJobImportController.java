package com.aiinterview.platform.controller;

import com.aiinterview.platform.common.response.ApiResponse;
import com.aiinterview.platform.model.dto.request.TopCvImportRequest;
import com.aiinterview.platform.model.dto.response.TopCvImportResult;
import com.aiinterview.platform.service.TopCvImportService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Cào JD từ TopCV (nút "Cào dữ liệu" ở màn kiểm duyệt). Chỉ ROLE_ADMIN; URL rule
 * /api/v1/admin/** đã ADMIN-only, annotation này là lớp thứ hai.
 */
@RestController
@RequestMapping("/api/v1/admin/jobs/import")
@RequiredArgsConstructor
public class AdminJobImportController {

    private final TopCvImportService topCvImportService;

    /** POST /api/v1/admin/jobs/import/topcv — body {"url": "https://www.topcv.vn/viec-lam/..."} */
    @PostMapping("/topcv")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<TopCvImportResult>> importTopCv(
            @Valid @RequestBody TopCvImportRequest request) {
        TopCvImportResult result = topCvImportService.importJob(request.getUrl());
        return ResponseEntity.ok(ApiResponse.success(
                "Đã cào JD \"" + result.getTitle() + "\" từ TopCV và tạo bản nháp", result));
    }
}
