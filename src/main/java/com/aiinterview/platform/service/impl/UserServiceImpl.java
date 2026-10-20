package com.aiinterview.platform.service.impl;

import com.aiinterview.platform.common.exception.BadRequestException;
import com.aiinterview.platform.common.exception.InvalidAccountException;
import com.aiinterview.platform.model.dto.request.ChangePasswordRequest;
import com.aiinterview.platform.model.entity.User;
import com.aiinterview.platform.model.repository.UserRepository;
import com.aiinterview.platform.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new InvalidAccountException("User not found with email: " + email));
    }

    @Override
    public User findByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new InvalidAccountException("User not found with email: " + email));
    }

    @Override
    public User findById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new InvalidAccountException(" User not found with id : " + id));
    }

    @Override
    @Transactional
    public void changePassword(String email, ChangePasswordRequest request) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new InvalidAccountException("User not found with email: " + email));
        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())) {
            throw new BadRequestException("Mật khẩu hiện tại không đúng");
        }
        validateNewPassword(request.getNewPassword());
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        // Thu hồi mọi phiên: refresh token chết ngay, access token hết hạn theo lịch của nó.
        user.setTokenVersion(user.getTokenVersion() + 1);
        userRepository.save(user);
    }

    /** Four independent rules so the message names exactly the one that failed (spec 008 US-04). */
    private static void validateNewPassword(String password) {
        if (password == null || password.length() < 8) {
            throw new BadRequestException("Mật khẩu mới phải có tối thiểu 8 ký tự");
        }
        boolean hasUpper = password.chars().anyMatch(Character::isUpperCase);
        boolean hasLower = password.chars().anyMatch(Character::isLowerCase);
        if (!hasUpper || !hasLower) {
            throw new BadRequestException("Mật khẩu mới phải chứa ít nhất một chữ hoa và một chữ thường");
        }
        if (password.chars().noneMatch(Character::isDigit)) {
            throw new BadRequestException("Mật khẩu mới phải chứa ít nhất một chữ số");
        }
        if (password.chars().noneMatch(c -> "@#$%^&+=!".indexOf(c) >= 0)) {
            throw new BadRequestException("Mật khẩu mới phải chứa ít nhất một ký tự đặc biệt (@#$%^&+=!)");
        }
    }
}
