package com.aiinterview.platform.controller;

import com.aiinterview.platform.common.response.ApiResponse;
import com.aiinterview.platform.model.dto.response.PermissionMatrixResponse;
import com.aiinterview.platform.service.AdminPermissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Read-only permission matrix (spec 006). There is deliberately no write endpoint. */
@RestController
@RequestMapping("/api/v1/admin/permissions")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminPermissionController {

    private final AdminPermissionService adminPermissionService;

    /** GET /api/v1/admin/permissions/matrix */
    @GetMapping("/matrix")
    public ResponseEntity<ApiResponse<PermissionMatrixResponse>> getMatrix() {
        return ResponseEntity.ok(ApiResponse.success(adminPermissionService.getMatrix()));
    }
}
