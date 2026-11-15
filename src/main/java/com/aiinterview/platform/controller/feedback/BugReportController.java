package com.aiinterview.platform.controller.feedback;

import com.aiinterview.platform.common.response.ApiResponse;
import com.aiinterview.platform.model.dto.request.BugReportRequest;
import com.aiinterview.platform.model.dto.request.BugReportUpdateRequest;
import com.aiinterview.platform.model.dto.response.BugReportResponse;
import com.aiinterview.platform.model.entity.User;
import com.aiinterview.platform.model.repository.UserRepository;
import com.aiinterview.platform.security.utils.SecurityUtils;
import com.aiinterview.platform.service.feedback.BugReportService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/bug-reports")
@RequiredArgsConstructor
public class BugReportController {

    private final BugReportService bugReportService;
    private final UserRepository userRepository;

    /** POST /api/v1/bug-reports — người dùng gửi báo cáo lỗi / góp ý */
    @PostMapping
    public ResponseEntity<ApiResponse<BugReportResponse>> create(@Valid @RequestBody BugReportRequest request) {
        User reporter = SecurityUtils.getCurrentUser(userRepository);
        return ResponseEntity.ok(ApiResponse.success(bugReportService.create(reporter, request)));
    }

    /** GET /api/v1/bug-reports/my — báo cáo của tôi */
    @GetMapping("/my")
    public ResponseEntity<ApiResponse<List<BugReportResponse>>> listMine() {
        User reporter = SecurityUtils.getCurrentUser(userRepository);
        return ResponseEntity.ok(ApiResponse.success(bugReportService.listMine(reporter)));
    }

    /** GET /api/v1/bug-reports/admin/all — toàn bộ báo cáo (quản trị) */
    @GetMapping("/admin/all")
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
    public ResponseEntity<ApiResponse<List<BugReportResponse>>> listAll() {
        return ResponseEntity.ok(ApiResponse.success(bugReportService.listAll()));
    }

    /** PATCH /api/v1/bug-reports/{id} — admin/staff cập nhật trạng thái & phản hồi */
    @PatchMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
    public ResponseEntity<ApiResponse<BugReportResponse>> update(
            @PathVariable Long id,
            @RequestBody BugReportUpdateRequest request) {
        User staff = SecurityUtils.getCurrentUser(userRepository);
        return ResponseEntity.ok(ApiResponse.success(bugReportService.update(staff, id, request)));
    }
}
