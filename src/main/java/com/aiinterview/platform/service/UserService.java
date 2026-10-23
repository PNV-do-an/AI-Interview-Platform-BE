package com.aiinterview.platform.service;

import com.aiinterview.platform.model.dto.request.ChangePasswordRequest;
import com.aiinterview.platform.model.entity.User;
import org.springframework.security.core.userdetails.UserDetailsService;

public interface UserService extends UserDetailsService {
    User findByEmail(String email);
    User findById(Long id);

    /** Verifies the current password, applies the strength rules, revokes every session. */
    void changePassword(String email, ChangePasswordRequest request);

    /** Logout: tăng tokenVersion để access lẫn refresh token đang phát hành đều chết ngay. */
    void revokeSessions(String email);
}
