package com.aiinterview.platform.service;

import com.aiinterview.platform.model.dto.response.UserSessionResponse;
import com.aiinterview.platform.model.entity.User;
import jakarta.servlet.http.HttpServletRequest;

import java.util.List;

/** Đăng nhập = một dòng `user_session`; thu hồi dòng đó là token của nó dùng không được nữa. */
public interface UserSessionService {

    /** Phát hành session id mới cho một lần đăng nhập/refresh và ghi nhật ký thiết bị + IP. */
    String start(User user, HttpServletRequest request);

    /** sid nào không có thật (legacy token, phát hành trước wave 010) thì coi là hợp lệ. */
    boolean isLive(String sessionId);

    void touch(String sessionId);

    List<UserSessionResponse> listForUser(Long userId, String currentSessionId);

    /** Thu hồi một phiên của chính người dùng; 404 nếu id không thuộc về họ. */
    void revoke(Long userId, Long sessionId);

    void revokeBySessionId(String sessionId);

    /** Dùng khi đổi mật khẩu, đặt lại mật khẩu, admin khóa hoặc tắt tài khoản. */
    void revokeAll(Long userId);
}
