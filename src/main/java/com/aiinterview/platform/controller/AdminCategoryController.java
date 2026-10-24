package com.aiinterview.platform.controller;

import com.aiinterview.platform.common.response.ApiResponse;
import com.aiinterview.platform.model.dto.response.CategoryGroupsResponse;
import com.aiinterview.platform.service.AdminCategoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Màn "Danh mục & Ngành nghề" của admin console (spec 009 FR-008).
 * Chỉ đọc qua GET vì schema vẫn là chuỗi tự do, không phải enum/bảng chuẩn.
 */
@RestController
@RequestMapping("/api/v1/admin/categories")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminCategoryController {

    private final AdminCategoryService adminCategoryService;

    /** GET /api/v1/admin/categories — giá trị đang dùng thật trên tin tuyển dụng chưa xóa. */
    @GetMapping
    public ResponseEntity<ApiResponse<CategoryGroupsResponse>> getCategories() {
        return ResponseEntity.ok(ApiResponse.success(adminCategoryService.getCategories()));
    }
}
