package com.aiinterview.platform.controller;

import com.aiinterview.platform.common.response.ApiResponse;
import com.aiinterview.platform.model.dto.request.PositionRequest;
import com.aiinterview.platform.model.dto.request.PositionSkillExtractionRequest;
import com.aiinterview.platform.model.dto.response.AdminPositionResponse;
import com.aiinterview.platform.service.AdminPositionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/positions")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminPositionController {

    private final AdminPositionService adminPositionService;

    /** GET /api/v1/admin/positions?page=0&size=20&search=backend&active=true */
    @GetMapping
    public ResponseEntity<ApiResponse<Page<AdminPositionResponse>>> getPositions(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Boolean active) {
        return ResponseEntity.ok(ApiResponse.success(
                adminPositionService.getPositions(page, size, search, active)));
    }

    /** GET /api/v1/admin/positions/{id} */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<AdminPositionResponse>> getPosition(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(adminPositionService.getPosition(id)));
    }

    /** POST /api/v1/admin/positions — tao vi tri, ky nang lay tu skills hoac AI trich xuat tu jdText */
    @PostMapping
    public ResponseEntity<ApiResponse<AdminPositionResponse>> createPosition(
            @Valid @RequestBody PositionRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Đã tạo vị trí",
                adminPositionService.createPosition(request)));
    }

    /** PUT /api/v1/admin/positions/{id} — cap nhat vi tri */
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<AdminPositionResponse>> updatePosition(
            @PathVariable Long id,
            @Valid @RequestBody PositionRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Đã cập nhật vị trí",
                adminPositionService.updatePosition(id, request)));
    }

    /** POST /api/v1/admin/positions/extract-skills — AI doc JD va tra ve ky nang yeu cau (chua luu) */
    @PostMapping("/extract-skills")
    public ResponseEntity<ApiResponse<List<String>>> extractSkills(
            @Valid @RequestBody PositionSkillExtractionRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                "Đã trích xuất kỹ năng từ JD",
                adminPositionService.extractSkills(request.getJdText())));
    }

    /** PATCH /api/v1/admin/positions/{id}/status?active=false — bat/tat hien thi trong recommendation */
    @PatchMapping("/{id}/status")
    public ResponseEntity<ApiResponse<AdminPositionResponse>> changeStatus(
            @PathVariable Long id,
            @RequestParam boolean active) {
        return ResponseEntity.ok(ApiResponse.success(
                active ? "Đã kích hoạt vị trí" : "Đã ngừng kích hoạt vị trí",
                adminPositionService.changeStatus(id, active)));
    }
}
