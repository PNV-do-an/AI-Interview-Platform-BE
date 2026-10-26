package com.aiinterview.platform.controller.user;

import com.aiinterview.platform.common.response.ApiResponse;
import com.aiinterview.platform.model.dto.request.AvatarUpdateRequest;
import com.aiinterview.platform.model.dto.request.ChangePasswordRequest;
import com.aiinterview.platform.model.dto.response.ProfileResponse;
import com.aiinterview.platform.model.dto.response.UserSessionResponse;
import com.aiinterview.platform.model.entity.User;
import com.aiinterview.platform.security.jwt.JwtFilter;
import com.aiinterview.platform.service.UserService;
import com.aiinterview.platform.service.UserSessionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/user")
public class ProfileController {

    private final UserService userService;
    private final UserSessionService userSessionService;

    public ProfileController(UserService userService, UserSessionService userSessionService) {
        this.userService = userService;
        this.userSessionService = userSessionService;
    }

    @GetMapping("/profile")
    public ProfileResponse profile(Authentication authentication) {
        User user = userService.findByEmail(authentication.getName());
        return new ProfileResponse(
                user.getEmail(),
                user.getFullName(),
                user.getPhone(),
                user.getLoginAttempts(),
                user.getRole().name()
        );
    }

    /** POST /api/user/change-password — works for every role; revokes all sessions on success. */
    @PostMapping("/change-password")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Void>> changePassword(
            Authentication authentication,
            @Valid @RequestBody ChangePasswordRequest request) {
        userService.changePassword(authentication.getName(), request);
        return ResponseEntity.ok(ApiResponse.success(
                "Đổi mật khẩu thành công. Mọi phiên đăng nhập cũ đã bị thu hồi, "
                        + "hãy đăng nhập lại bằng mật khẩu mới.", null));
    }

    /** GET /api/user/sessions — "Thiết bị đăng nhập hoạt động"; bản đang gọi được đánh dấu current. */
    @GetMapping("/sessions")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<UserSessionResponse>>> sessions(
            Authentication authentication, HttpServletRequest httpRequest) {
        User user = userService.findByEmail(authentication.getName());
        return ResponseEntity.ok(ApiResponse.success("ok",
                userSessionService.listForUser(user.getId(), currentSessionId(httpRequest))));
    }

    /**
     * DELETE /api/user/sessions/{sessionId} — thu hồi một thiết bị. id là số dòng trả về từ
     * GET /sessions, không phải sid trong token; gọi vào phiên đang dùng thì request kế tiếp phải
     * đăng nhập lại. id không thuộc tài khoản này trả 404.
     */
    @DeleteMapping("/sessions/{sessionId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Void>> revokeSession(Authentication authentication,
                                                           @PathVariable Long sessionId) {
        User user = userService.findByEmail(authentication.getName());
        userSessionService.revoke(user.getId(), sessionId);
        return ResponseEntity.ok(ApiResponse.success("Đã thu hồi phiên đăng nhập trên thiết bị đó", null));
    }

    /** PUT /api/user/avatar — ảnh gửi lên dạng data URL; loại và cỡ ảnh bị chặn ngay trong request này. */
    @PutMapping("/avatar")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Void>> updateAvatar(Authentication authentication,
                                                          @Valid @RequestBody AvatarUpdateRequest request) {
        userService.updateAvatar(authentication.getName(), request.getDataUrl());
        return ResponseEntity.ok(ApiResponse.success("Đã cập nhật ảnh đại diện", null));
    }

    /** DELETE /api/user/avatar */
    @DeleteMapping("/avatar")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Void>> deleteAvatar(Authentication authentication) {
        userService.clearAvatar(authentication.getName());
        return ResponseEntity.ok(ApiResponse.success("Đã xóa ảnh đại diện", null));
    }

    private static String currentSessionId(HttpServletRequest request) {
        return request.getAttribute(JwtFilter.SESSION_ID_ATTRIBUTE) instanceof String sessionId
                ? sessionId
                : null;
    }
}
