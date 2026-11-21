package com.aiinterview.platform.security.utils;

import com.aiinterview.platform.common.exception.ResourceNotFoundException;
import com.aiinterview.platform.model.entity.User;
import com.aiinterview.platform.model.enums.Role;
import com.aiinterview.platform.model.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Two static helpers that every service calls: the caller's IP for the login rate limiter, and the
 * caller's User row for ownership checks. The real SecurityContextHolder is used instead of
 * mockStatic — the helper only reads the authentication name.
 */
@ExtendWith(MockitoExtension.class)
class SecurityUtilsTest {

    private static final String EMAIL = "candidate@example.com";

    @Mock
    private UserRepository userRepository;
    @Mock
    private HttpServletRequest request;

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Chi lay IP dau tien trong chuoi X-Forwarded-For")
    void getClientIP_returnsFirstForwardedHop() {
        when(request.getHeader("X-Forwarded-For")).thenReturn("203.0.113.9, 10.0.0.1, 10.0.0.2");

        assertThat(SecurityUtils.getClientIP(request)).isEqualTo("203.0.113.9");
    }

    @Test
    @DisplayName("IP trong chuoi X-Forwarded-For duoc trim khoang trang")
    void getClientIP_trimsWhitespace() {
        when(request.getHeader("X-Forwarded-For")).thenReturn("  203.0.113.9 , 10.0.0.1");

        assertThat(SecurityUtils.getClientIP(request)).isEqualTo("203.0.113.9");
    }

    @Test
    @DisplayName("Khong co proxy thi lay remoteAddr")
    void getClientIP_withoutHeader_usesRemoteAddress() {
        when(request.getRemoteAddr()).thenReturn("198.51.100.7");

        assertThat(SecurityUtils.getClientIP(request)).isEqualTo("198.51.100.7");
    }

    @Test
    @DisplayName("Header X-Forwarded-For rong coi nhu khong co proxy")
    void getClientIP_withEmptyHeader_usesRemoteAddress() {
        when(request.getHeader("X-Forwarded-For")).thenReturn("");
        when(request.getRemoteAddr()).thenReturn("198.51.100.7");

        assertThat(SecurityUtils.getClientIP(request)).isEqualTo("198.51.100.7");
    }

    @Test
    @DisplayName("Chua dang nhap thi nem ResourceNotFoundException, khong cham vao users")
    void getCurrentUser_withoutAuthentication_throws() {
        assertThatThrownBy(() -> SecurityUtils.getCurrentUser(userRepository))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Người dùng chưa đăng nhập");
        verifyNoInteractions(userRepository);
    }

    @Test
    @DisplayName("Dang nhap nhung email khong con trong users thi bao ro email")
    void getCurrentUser_unknownEmail_throwsWithEmail() {
        signIn(EMAIL);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> SecurityUtils.getCurrentUser(userRepository))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Không tìm thấy người dùng: " + EMAIL);
    }

    @Test
    @DisplayName("Dang nhap hop le thi tra ve dung dong users")
    void getCurrentUser_returnsAuthenticatedUser() {
        User user = User.builder().id(5L).email(EMAIL).fullName("Candidate").role(Role.ROLE_USER).build();
        signIn(EMAIL);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));

        assertThat(SecurityUtils.getCurrentUser(userRepository)).isSameAs(user);
    }

    private static void signIn(String email) {
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(email, null));
    }
}
