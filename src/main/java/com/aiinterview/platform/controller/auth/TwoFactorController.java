package com.aiinterview.platform.controller.auth;

import com.aiinterview.platform.common.response.ApiResponse;
import com.aiinterview.platform.model.dto.request.TwoFactorCodeRequest;
import com.aiinterview.platform.model.dto.response.TwoFactorEnableResponse;
import com.aiinterview.platform.model.dto.response.TwoFactorSetupResponse;
import com.aiinterview.platform.model.dto.response.TwoFactorStatusResponse;
import com.aiinterview.platform.service.TwoFactorService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Self-service 2FA — mỗi endpoint chỉ tác động lên chính tài khoản đang đăng nhập. */
@RestController
@RequestMapping("/api/v1/auth/2fa")
@PreAuthorize("isAuthenticated()")
@RequiredArgsConstructor
public class TwoFactorController {

    private final TwoFactorService twoFactorService;

    /** GET /api/v1/auth/2fa — trạng thái bật/tắt cho card "Bảo mật" của FE. */
    @GetMapping
    public ResponseEntity<ApiResponse<TwoFactorStatusResponse>> status(Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.success(twoFactorService.getStatus(authentication.getName())));
    }

    /** POST /api/v1/auth/2fa/setup — trả secret + otpauth URI, FE tự render QR. */
    @PostMapping("/setup")
    public ResponseEntity<ApiResponse<TwoFactorSetupResponse>> setup(Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.success("Quét mã QR bằng ứng dụng xác thực rồi nhập OTP để bật",
                twoFactorService.setup(authentication.getName())));
    }

    /** POST /api/v1/auth/2fa/enable — xác minh OTP đầu tiên, trả recovery codes duy nhất một lần. */
    @PostMapping("/enable")
    public ResponseEntity<ApiResponse<TwoFactorEnableResponse>> enable(
            Authentication authentication, @Valid @RequestBody TwoFactorCodeRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                "Đã bật 2FA. Lưu lại các mã dự phòng — chúng chỉ hiển thị một lần.",
                twoFactorService.enable(authentication.getName(), request.getCode())));
    }

    /** POST /api/v1/auth/2fa/disable — cần OTP hiện tại hoặc một recovery code còn hiệu lực. */
    @PostMapping("/disable")
    public ResponseEntity<ApiResponse<Void>> disable(
            Authentication authentication, @Valid @RequestBody TwoFactorCodeRequest request) {
        twoFactorService.disable(authentication.getName(), request.getCode());
        return ResponseEntity.ok(ApiResponse.success("Đã tắt 2FA"));
    }
}
