package com.aiinterview.platform.controller;

import com.aiinterview.platform.common.response.ApiResponse;
import com.aiinterview.platform.model.dto.request.TopCvBatchImportRequest;
import com.aiinterview.platform.model.dto.request.TopCvImportRequest;
import com.aiinterview.platform.model.dto.response.TopCvBatchImportResult;
import com.aiinterview.platform.model.dto.response.TopCvImportResult;
import com.aiinterview.platform.service.TopCvImportService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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

    /**
     * POST /api/v1/admin/jobs/import/topcv/batch
     * Body: {"url":"https://www.topcv.vn/tim-viec-lam-it","limit":20}
     * hoặc {"urls":["https://www.topcv.vn/viec-lam/...","..."]}.
     * Một URL trang danh sách sẽ được quét link bài đăng và cào tối đa {@code limit} tin.
     */
    @PostMapping("/topcv/batch")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<TopCvBatchImportResult>> importTopCvBatch(
            @Valid @RequestBody TopCvBatchImportRequest request) {
        TopCvBatchImportResult result = topCvImportService.importJobs(request);
        return ResponseEntity.ok(ApiResponse.success(
                "Đã cào " + result.getImported() + " JD từ TopCV và tạo bản nháp", result));
    }

    /**
     * POST /api/v1/admin/jobs/import/topcv/auto?limit=20
     * Một nút bấm: không cần URL, BE tự dùng danh sách JD IT mặc định
     * (app.topcv.it-listing-url) rồi cào tối đa {@code limit} tin.
     */
    @PostMapping("/topcv/auto")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<TopCvBatchImportResult>> importTopCvAuto(
            @RequestParam(value = "limit", required = false, defaultValue = "0") int limit) {
        TopCvBatchImportResult result = topCvImportService.importDefaultItJobs(limit);
        return ResponseEntity.ok(ApiResponse.success(
                "Đã cào " + result.getImported() + " JD IT từ TopCV và tạo bản nháp", result));
    }
}
