package com.aiinterview.platform.service;

import com.aiinterview.platform.model.dto.response.LoginResponse;
import com.aiinterview.platform.model.dto.response.TwoFactorEnableResponse;
import com.aiinterview.platform.model.dto.response.TwoFactorSetupResponse;
import com.aiinterview.platform.model.dto.response.TwoFactorStatusResponse;
import com.aiinterview.platform.model.entity.User;
import jakarta.servlet.http.HttpServletRequest;

public interface TwoFactorService {

    /**
     * Bước hai của đăng nhập: challenge token (5 phút) + OTP/recovery code đúng → JWT thật.
     * Sai mã bị đếm vào cùng bộ rate-limit với mật khẩu (login_attempts).
     */
    LoginResponse verifyLoginOtp(String challengeToken, String code, HttpServletRequest request);

    TwoFactorStatusResponse getStatus(String email);

    /** Sinh secret mới (ghi tạm vào user, chưa bật) + otpauth URI cho FE render QR. */
    TwoFactorSetupResponse setup(String email);

    /** Xác minh OTP đúng với secret đang chờ → bật 2FA + phát hành recovery codes (duy nhất một lần). */
    TwoFactorEnableResponse enable(String email, String code);

    /** Muốn tắt 2FA phải chứng minh đang giữ thiết bị: OTP hoặc recovery code còn hiệu lực. */
    void disable(String email, String code);

    /**
     * OTP đúng secret HOẶC recovery code còn hiệu lực; recovery code bị đánh dấu dùng ngay tại đây.
     * Dùng cho bước hai của luồng đăng nhập (verify-otp).
     */
    boolean verifySecondFactor(User user, String code);
}
