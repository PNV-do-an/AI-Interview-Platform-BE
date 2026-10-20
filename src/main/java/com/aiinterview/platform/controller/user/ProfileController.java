package com.aiinterview.platform.controller.user;

import com.aiinterview.platform.common.response.ApiResponse;
import com.aiinterview.platform.model.dto.request.ChangePasswordRequest;
import com.aiinterview.platform.model.dto.response.ProfileResponse;
import com.aiinterview.platform.model.entity.User;
import com.aiinterview.platform.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/user")
public class ProfileController {

    private final UserService userService;

    public ProfileController(UserService userService) {
        this.userService = userService;
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
}
