package com.aiinterview.platform.controller.commerce;

import com.aiinterview.platform.common.response.ApiResponse;
import com.aiinterview.platform.model.dto.request.ServicePackageRequest;
import com.aiinterview.platform.model.dto.response.ServicePackageResponse;
import com.aiinterview.platform.service.commerce.ServicePackageService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/packages")
@RequiredArgsConstructor
public class ServicePackageController {

    private final ServicePackageService servicePackageService;

    /** GET /api/v1/packages — danh sách gói đang mở bán (mọi user đã đăng nhập) */
    @GetMapping
    public ResponseEntity<ApiResponse<List<ServicePackageResponse>>> listActive() {
        return ResponseEntity.ok(ApiResponse.success(servicePackageService.listActive()));
    }

    /** GET /api/v1/packages/all — toàn bộ gói kể cả đã vô hiệu hóa */
    @GetMapping("/all")
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
    public ResponseEntity<ApiResponse<List<ServicePackageResponse>>> listAll() {
        return ResponseEntity.ok(ApiResponse.success(servicePackageService.listAll()));
    }

    /** POST /api/v1/packages — tạo gói mới */
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
    public ResponseEntity<ApiResponse<ServicePackageResponse>> create(
            @Valid @RequestBody ServicePackageRequest request) {
        return ResponseEntity.ok(ApiResponse.success(servicePackageService.create(request)));
    }

    /** PUT /api/v1/packages/{id} — cập nhật gói */
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
    public ResponseEntity<ApiResponse<ServicePackageResponse>> update(
            @PathVariable Long id,
            @Valid @RequestBody ServicePackageRequest request) {
        return ResponseEntity.ok(ApiResponse.success(servicePackageService.update(id, request)));
    }

    /** PATCH /api/v1/packages/{id}/toggle-active — bật/tắt gói */
    @PatchMapping("/{id}/toggle-active")
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
    public ResponseEntity<ApiResponse<ServicePackageResponse>> toggleActive(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(servicePackageService.toggleActive(id)));
    }

    /** DELETE /api/v1/packages/{id} — xóa gói chưa có đơn hàng */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        servicePackageService.delete(id);
        return ResponseEntity.ok(ApiResponse.success("Đã xóa gói dịch vụ", null));
    }
}
