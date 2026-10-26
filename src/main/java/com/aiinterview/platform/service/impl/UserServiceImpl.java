package com.aiinterview.platform.service.impl;

import com.aiinterview.platform.common.exception.BadRequestException;
import com.aiinterview.platform.common.exception.InvalidAccountException;
import com.aiinterview.platform.model.dto.request.ChangePasswordRequest;
import com.aiinterview.platform.model.entity.User;
import com.aiinterview.platform.model.repository.UserRepository;
import com.aiinterview.platform.service.UserService;
import com.aiinterview.platform.service.UserSessionService;
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
    private final UserSessionService userSessionService;

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
        // Thu hồi mọi phiên: claim "tv" trong access lẫn refresh token cũ lệch với cột này nên
        // JwtFilter từ chối ngay, đồng thời các dòng user_session thành revoked để màn thiết bị
        // và đường /refresh cùng nhìn thấy một trạng thái.
        user.setTokenVersion(user.getTokenVersion() + 1);
        userRepository.save(user);
        userSessionService.revokeAll(user.getId());
    }

    @Override
    @Transactional
    public void revokeSessions(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new InvalidAccountException("User not found with email: " + email));
        user.setTokenVersion(user.getTokenVersion() + 1);
        userRepository.save(user);
        userSessionService.revokeAll(user.getId());
    }

    @Override
    @Transactional
    public void updateAvatar(String email, String dataUrl) {
        User user = findByEmail(email);
        user.setAvatar(checkedAvatar(dataUrl));
        userRepository.save(user);
    }

    @Override
    @Transactional
    public void clearAvatar(String email) {
        User user = findByEmail(email);
        user.setAvatar(null);
        userRepository.save(user);
    }

    /**
     * Ảnh phải đúng loại và cỡ ngay trong request ghi — không lưu chuỗi hỏng rồi mới lộ ra lúc
     * màn hình hiển thị. Nhãn MIME tự khai không được tin: kiểm tra chữ ký tệp sau khi decode.
     */
    private static String checkedAvatar(String dataUrl) {
        int comma = dataUrl.indexOf(',');
        if (comma < 0) {
            throw new BadRequestException("Ảnh đại diện phải là dữ liệu base64");
        }
        byte[] bytes;
        try {
            bytes = java.util.Base64.getDecoder().decode(dataUrl.substring(comma + 1));
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Ảnh đại diện không đọc được");
        }
        if (bytes.length == 0) {
            throw new BadRequestException("Ảnh đại diện rỗng");
        }
        if (bytes.length > MAX_AVATAR_BYTES) {
            throw new BadRequestException("Ảnh đại diện tối đa 2 MB");
        }
        if (!isPng(bytes) && !isJpeg(bytes) && !isWebp(bytes)) {
            throw new BadRequestException("Chỉ nhận ảnh PNG, JPG hoặc WEBP");
        }
        return dataUrl;
    }

    private static final int MAX_AVATAR_BYTES = 2 * 1024 * 1024;

    private static boolean isPng(byte[] b) {
        return b.length > 8 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G';
    }

    private static boolean isJpeg(byte[] b) {
        return b.length > 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF;
    }

    /** RIFF????WEBP — 4 byte độ dài nằm giữa, không cần đọc tới. */
    private static boolean isWebp(byte[] b) {
        return b.length > 12 && b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F'
                && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P';
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
