package com.aiinterview.platform.service;

import com.aiinterview.platform.model.dto.request.ChangePasswordRequest;
import com.aiinterview.platform.model.entity.User;
import org.springframework.security.core.userdetails.UserDetailsService;

public interface UserService extends UserDetailsService {
    User findByEmail(String email);
    User findById(Long id);

    /** Verifies the current password, applies the strength rules, revokes every session. */
    void changePassword(String email, ChangePasswordRequest request);

    /** Logout: tăng tokenVersion để access lẫn refresh token đang phát hành đều chết ngay,
     *  đồng thời đóng mọi dòng user_session của tài khoản. */
    void revokeSessions(String email);

    /**
     * Lưu ảnh đại diện dạng data URL. Loại ảnh và cỡ ảnh được kiểm tra trước khi ghi:
     * chuỗi gửi lên không decode được, không phải PNG/JPG/WEBP, hoặc vượt 2 MB là fail ngay.
     */
    void updateAvatar(String email, String dataUrl);

    /** Xóa ảnh; màn tài khoản quay lại hiển thị chữ viết tắt từ họ tên. */
    void clearAvatar(String email);
}
