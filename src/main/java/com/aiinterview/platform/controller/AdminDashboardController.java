package com.aiinterview.platform.controller;

import com.aiinterview.platform.common.exception.BadRequestException;
import com.aiinterview.platform.common.response.ApiResponse;
import com.aiinterview.platform.model.dto.response.DashboardResponse;
import com.aiinterview.platform.service.AdminDashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/dashboard")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminDashboardController {

    private static final List<Integer> ALLOWED_RANGES = List.of(7, 30, 90);

    private final AdminDashboardService adminDashboardService;

    /** GET /api/v1/admin/dashboard?days=30 */
    @GetMapping
    public ResponseEntity<ApiResponse<DashboardResponse>> dashboard(
            @RequestParam(defaultValue = "30") int days) {
        if (!ALLOWED_RANGES.contains(days)) {
            throw new BadRequestException("Tham số days chỉ nhận một trong: 7, 30, 90");
        }
        return ResponseEntity.ok(ApiResponse.success(adminDashboardService.getDashboard(days)));
    }
}
