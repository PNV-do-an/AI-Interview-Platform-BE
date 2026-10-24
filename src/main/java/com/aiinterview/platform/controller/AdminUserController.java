package com.aiinterview.platform.controller;

import com.aiinterview.platform.common.exception.BadRequestException;
import com.aiinterview.platform.common.response.ApiResponse;
import com.aiinterview.platform.model.dto.request.AdminDisable2faRequest;
import com.aiinterview.platform.model.dto.request.AdminInviteUserRequest;
import com.aiinterview.platform.model.dto.request.AdminUpdateProfileRequest;
import com.aiinterview.platform.model.dto.request.AuditLogQuery;
import com.aiinterview.platform.model.dto.request.BulkRoleChangeRequest;
import com.aiinterview.platform.model.dto.request.BulkUserActionRequest;
import com.aiinterview.platform.model.dto.request.ChangeRoleRequest;
import com.aiinterview.platform.model.dto.request.LockAccountRequest;
import com.aiinterview.platform.model.dto.request.UserStatusRequest;
import com.aiinterview.platform.model.dto.response.AdminProfileUpdateResponse;
import com.aiinterview.platform.model.dto.response.AdminUserStatsResponse;
import com.aiinterview.platform.model.dto.response.AuditLogResponse;
import com.aiinterview.platform.model.dto.response.BulkUserActionResult;
import com.aiinterview.platform.model.dto.response.UserActivityResponse;
import com.aiinterview.platform.model.dto.response.UserDetailResponse;
import com.aiinterview.platform.model.dto.response.UserSummaryResponse;
import com.aiinterview.platform.model.enums.AuditAction;
import com.aiinterview.platform.model.enums.Role;
import com.aiinterview.platform.model.enums.UserStatusFilter;
import com.aiinterview.platform.service.AdminUserService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasRole('ADMIN')")
@Validated
@RequiredArgsConstructor
public class AdminUserController {

    private static final int MAX_PAGE_SIZE = 100;

    private final AdminUserService adminUserService;

    /** GET /api/v1/admin/users?page=0&size=20&search=...&status=ACTIVE&role=ROLE_USER */
    @GetMapping("/users")
    public ResponseEntity<ApiResponse<Page<UserSummaryResponse>>> getUsers(
            @RequestParam(defaultValue = "0") @Min(value = 0, message = "page không được âm") int page,
            @RequestParam(defaultValue = "20") @Min(value = 1, message = "size phải ≥ 1")
            @Max(value = MAX_PAGE_SIZE, message = "size tối đa là 100") int size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) UserStatusFilter status,
            @RequestParam(required = false) Role role) {
        Page<UserSummaryResponse> users = adminUserService.getUsers(page, size, search, status, role);
        return ResponseEntity.ok(ApiResponse.success(users));
    }

    /** GET /api/v1/admin/users/deleted?page=0&size=20&search=...&role=ROLE_USER */
    @GetMapping("/users/deleted")
    public ResponseEntity<ApiResponse<Page<UserSummaryResponse>>> getDeletedUsers(
            @RequestParam(defaultValue = "0") @Min(value = 0, message = "page không được âm") int page,
            @RequestParam(defaultValue = "20") @Min(value = 1, message = "size phải ≥ 1")
            @Max(value = MAX_PAGE_SIZE, message = "size tối đa là 100") int size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Role role) {
        return ResponseEntity.ok(ApiResponse.success(adminUserService.getDeletedUsers(page, size, search, role)));
    }

    /** GET /api/v1/admin/users/stats?search=...&role=ROLE_USER — badge counts for the tabs and KPIs. */
    @GetMapping("/users/stats")
    public ResponseEntity<ApiResponse<AdminUserStatsResponse>> getUserStats(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Role role) {
        return ResponseEntity.ok(ApiResponse.success(adminUserService.getStats(search, role)));
    }

    /** GET /api/v1/admin/users/export?search=...&role=...&status=... — same rows as the list, capped. */
    @GetMapping("/users/export")
    public ResponseEntity<byte[]> exportUsers(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Role role,
            @RequestParam(required = false) UserStatusFilter status) {
        String csv = adminUserService.exportUsers(search, status, role);
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"users-export.csv\"")
                .body(csv.getBytes(StandardCharsets.UTF_8));
    }

    /** POST /api/v1/admin/users — invite a member; the password is chosen by the invitee (C-4). */
    @PostMapping("/users")
    public ResponseEntity<ApiResponse<UserDetailResponse>> inviteUser(
            @Valid @RequestBody AdminInviteUserRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Đã gửi email mời tài khoản",
                adminUserService.inviteUser(request)));
    }

    /** POST /api/v1/admin/users/{id}/invitation/resend */
    @PostMapping("/users/{id}/invitation/resend")
    public ResponseEntity<ApiResponse<UserDetailResponse>> resendInvitation(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("Đã gửi lại email mời",
                adminUserService.resendInvitation(id)));
    }

    /** DELETE /api/v1/admin/users/{id}/invitation — kill the link, keep the account. */
    @DeleteMapping("/users/{id}/invitation")
    public ResponseEntity<ApiResponse<UserDetailResponse>> revokeInvitation(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("Đã thu hồi link mời",
                adminUserService.revokeInvitation(id)));
    }

    /** PATCH /api/v1/admin/users/{id} — edit fullName / email / phone of a member. */
    @PatchMapping("/users/{id}")
    public ResponseEntity<ApiResponse<AdminProfileUpdateResponse>> updateProfile(
            @PathVariable Long id,
            @Valid @RequestBody AdminUpdateProfileRequest request) {
        AdminProfileUpdateResponse result = adminUserService.updateProfile(id, request);
        String message = result.emailChanged()
                ? "Hồ sơ đã được cập nhật. Email mới chưa được xác thực: tài khoản chuyển sang trạng thái "
                + "chờ kích hoạt, link mời đã được gửi và phiên đăng nhập cũ không còn hiệu lực."
                : "Hồ sơ đã được cập nhật";
        return ResponseEntity.ok(ApiResponse.success(message, result));
    }

    /** GET /api/v1/admin/users/{id} */
    @GetMapping("/users/{id}")
    public ResponseEntity<ApiResponse<UserDetailResponse>> getUserDetail(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(adminUserService.getUserDetail(id)));
    }

    /** GET /api/v1/admin/users/{id}/activity — merged timeline of the account's activity (009 FR-003). */
    @GetMapping("/users/{id}/activity")
    public ResponseEntity<ApiResponse<Page<UserActivityResponse>>> getUserActivity(
            @PathVariable Long id,
            @RequestParam(defaultValue = "0") @Min(value = 0, message = "page không được âm") int page,
            @RequestParam(defaultValue = "20") @Min(value = 1, message = "size phải ≥ 1")
            @Max(value = 100, message = "size tối đa là 100") int size) {
        return ResponseEntity.ok(ApiResponse.success(adminUserService.getUserActivity(id, page, size)));
    }

    /**
     * PATCH /api/v1/admin/users/{id}/lock — body is optional: {"reason": "...", "notifyUser": true}.
     * Without a body the legacy behaviour applies (lock + notification email).
     */
    @PatchMapping("/users/{id}/lock")
    public ResponseEntity<ApiResponse<UserDetailResponse>> lockUser(
            @PathVariable Long id,
            @Valid @RequestBody(required = false) LockAccountRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Tài khoản đã bị khóa",
                adminUserService.lockUser(id, request)));
    }

    /** PATCH /api/v1/admin/users/{id}/unlock */
    @PatchMapping("/users/{id}/unlock")
    public ResponseEntity<ApiResponse<UserDetailResponse>> unlockUser(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("Tài khoản đã được mở khóa", adminUserService.unlockUser(id)));
    }

    /** POST /api/v1/admin/users/bulk-lock body: {"ids": [...], "reason": "...", "notifyUser": true} */
    @PostMapping("/users/bulk-lock")
    public ResponseEntity<ApiResponse<BulkUserActionResult>> bulkLock(
            @Valid @RequestBody BulkUserActionRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Đã xử lý khóa hàng loạt",
                adminUserService.bulkLock(request)));
    }

    /** POST /api/v1/admin/users/bulk-unlock body: {"ids": [...]} */
    @PostMapping("/users/bulk-unlock")
    public ResponseEntity<ApiResponse<BulkUserActionResult>> bulkUnlock(
            @Valid @RequestBody BulkUserActionRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Đã xử lý mở khóa hàng loạt",
                adminUserService.bulkUnlock(request)));
    }

    /** PATCH /api/v1/admin/users/bulk-role body: {"ids": [...], "role": "ROLE_STAFF"} */
    @PatchMapping("/users/bulk-role")
    public ResponseEntity<ApiResponse<BulkUserActionResult>> bulkRole(
            @Valid @RequestBody BulkRoleChangeRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Đã xử lý đổi vai trò hàng loạt",
                adminUserService.bulkRole(request)));
    }

    /** PATCH /api/v1/admin/users/{id}/status  body: {"enabled": true|false} */
    @PatchMapping("/users/{id}/status")
    public ResponseEntity<ApiResponse<UserDetailResponse>> setUserStatus(
            @PathVariable Long id,
            @Valid @RequestBody UserStatusRequest request) {
        boolean enabled = request.getEnabled();
        String message = enabled ? "Tài khoản đã được bật" : "Tài khoản đã được tắt";
        return ResponseEntity.ok(ApiResponse.success(message, adminUserService.setUserEnabled(id, enabled)));
    }

    /** DELETE /api/v1/admin/users/{id} */
    @DeleteMapping("/users/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteUser(@PathVariable Long id) {
        adminUserService.deleteUser(id);
        return ResponseEntity.ok(ApiResponse.success("Tài khoản đã được xóa", null));
    }

    /** POST /api/v1/admin/users/{id}/restore */
    @PostMapping("/users/{id}/restore")
    public ResponseEntity<ApiResponse<UserDetailResponse>> restoreUser(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("Tài khoản đã được khôi phục",
                adminUserService.restoreUser(id)));
    }

    /** POST /api/v1/admin/users/{id}/reset-password */
    @PostMapping("/users/{id}/reset-password")
    public ResponseEntity<ApiResponse<Void>> resetPassword(@PathVariable Long id) {
        adminUserService.resetPassword(id);
        return ResponseEntity.ok(ApiResponse.success("Email đặt lại mật khẩu đã được gửi", null));
    }

    /** PATCH /api/v1/admin/users/{id}/role */
    @PatchMapping("/users/{id}/role")
    public ResponseEntity<ApiResponse<UserDetailResponse>> changeRole(
            @PathVariable Long id,
            @Valid @RequestBody ChangeRoleRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Vai trò đã được cập nhật",
                adminUserService.changeRole(id, request.getRole())));
    }

    /** PATCH /api/v1/admin/users/{id}/2fa — force-disable 2FA (user mất thiết bị), bắt buộc lý do. */
    @PatchMapping("/users/{id}/2fa")
    public ResponseEntity<ApiResponse<UserDetailResponse>> disable2fa(
            @PathVariable Long id,
            @Valid @RequestBody AdminDisable2faRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Đã tắt 2FA của tài khoản",
                adminUserService.disableUserTwoFactor(id, request.getReason())));
    }

    /**
     * GET /api/v1/admin/audit-logs?page=0&size=20&adminId=&targetUserId=&action=&from=&to=
     * from/to accept yyyy-MM-dd or yyyy-MM-ddTHH:mm:ss.
     */
    @GetMapping("/audit-logs")
    public ResponseEntity<ApiResponse<Page<AuditLogResponse>>> getAuditLogs(
            @RequestParam(defaultValue = "0") @Min(value = 0, message = "page không được âm") int page,
            @RequestParam(defaultValue = "20") @Min(value = 1, message = "size phải ≥ 1")
            @Max(value = MAX_PAGE_SIZE, message = "size tối đa là 100") int size,
            @RequestParam(required = false) Long adminId,
            @RequestParam(required = false) Long targetUserId,
            @RequestParam(required = false) AuditAction action,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        LocalDateTime createdFrom = parseDate(from, "from", false);
        LocalDateTime createdTo = parseDate(to, "to", true);
        if (createdFrom != null && createdTo != null && createdFrom.isAfter(createdTo)) {
            throw new BadRequestException("from không được lớn hơn to");
        }
        AuditLogQuery query = new AuditLogQuery(page, size, adminId, targetUserId, action, createdFrom, createdTo);
        return ResponseEntity.ok(ApiResponse.success(adminUserService.getAuditLogs(query)));
    }

    /** A date-only "to" value means end of that day, so the filter reads like the admin expects. */
    private LocalDateTime parseDate(String value, String parameter, boolean endOfDay) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        try {
            if (trimmed.length() == 10) {
                LocalDate date = LocalDate.parse(trimmed);
                return endOfDay ? date.atTime(23, 59, 59) : date.atStartOfDay();
            }
            return LocalDateTime.parse(trimmed);
        } catch (RuntimeException e) {
            throw new BadRequestException("Tham số '" + parameter + "' không hợp lệ: cần định dạng "
                    + "yyyy-MM-dd hoặc yyyy-MM-ddTHH:mm:ss");
        }
    }
}
