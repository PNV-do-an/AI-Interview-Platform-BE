package com.aiinterview.platform.service.impl;

import com.aiinterview.platform.common.exception.MailDeliveryException;
import com.aiinterview.platform.model.dto.response.PasswordResetResponse;
import com.aiinterview.platform.model.entity.PasswordResetAttempt;
import com.aiinterview.platform.model.entity.User;
import com.aiinterview.platform.model.enums.Role;
import com.aiinterview.platform.model.repository.PasswordResetAttemptRepository;
import com.aiinterview.platform.model.repository.UserRepository;
import com.aiinterview.platform.service.EmailService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * /auth/forgot-password luôn trả cùng một thông báo, vì vậy hợp đồng chống dò email và
 * cửa sổ đếm lượt yêu cầu phải được chốt ở tầng service.
 */
@ExtendWith(MockitoExtension.class)
class PasswordResetServiceImplTest {

    private static final String EMAIL = "candidate@example.com";
    private static final String FULL_NAME = "Jane Doe";
    private static final String IP = "203.0.113.40";
    private static final String GENERIC = "Nếu email tồn tại, bạn sẽ nhận được link đặt lại mật khẩu.";

    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordResetAttemptRepository resetAttemptRepository;
    @Mock
    private EmailService emailService;
    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private PasswordResetServiceImpl passwordResetService;

    private MockHttpServletRequest request(String ip) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(ip);
        return request;
    }

    private User user() {
        return User.builder()
                .id(7L)
                .email(EMAIL)
                .password("old-hash")
                .fullName(FULL_NAME)
                .role(Role.ROLE_USER)
                .enabled(true)
                .build();
    }

    private void noRequestsYet() {
        when(resetAttemptRepository.countByAccountAndCreatedAtAfter(anyString(), any())).thenReturn(0L);
        when(resetAttemptRepository.countByAddressAndCreatedAtAfter(anyString(), any())).thenReturn(0L);
    }

    @Test
    void requestWithinWindowIssuesTokenAndRecordsTheAttempt() {
        noRequestsYet();
        User user = user();
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));

        String message = passwordResetService.forgotPassword(EMAIL, request(IP));

        assertEquals(GENERIC, message);
        assertNotNull(user.getResetToken());
        assertTrue(user.getResetToken().length() >= 32);
        long minutes = Duration.between(LocalDateTime.now(), user.getResetTokenExpiry()).toMinutes();
        assertTrue(minutes > 28 && minutes <= 30, "Token phải có hạn 30 phút, đo được " + minutes);
        verify(userRepository).save(user);
        verify(emailService).sendResetPasswordEmail(EMAIL, FULL_NAME, user.getResetToken());

        ArgumentCaptor<PasswordResetAttempt> attempt = ArgumentCaptor.forClass(PasswordResetAttempt.class);
        verify(resetAttemptRepository).save(attempt.capture());
        assertEquals(IP, attempt.getValue().getAddress());
        assertEquals(EMAIL, attempt.getValue().getAccount());
        assertNotNull(attempt.getValue().getCreatedAt());
    }

    @Test
    void fifthEmailRequestStillSendsButSixthIsThrottledWithoutNewToken() {
        when(resetAttemptRepository.countByAccountAndCreatedAtAfter(eq(EMAIL), any())).thenReturn(5L);
        when(resetAttemptRepository.countByAddressAndCreatedAtAfter(eq(IP), any())).thenReturn(5L);

        String message = passwordResetService.forgotPassword(EMAIL, request(IP));

        assertEquals(GENERIC, message);
        verify(userRepository, never()).findByEmail(anyString());
        verify(userRepository, never()).save(any(User.class));
        verify(emailService, never()).sendResetPasswordEmail(anyString(), anyString(), anyString());
        verify(resetAttemptRepository).save(any(PasswordResetAttempt.class));
    }

    @Test
    void tenthIpRequestStillSendsButEleventhIsThrottledForAFreshEmail() {
        when(resetAttemptRepository.countByAccountAndCreatedAtAfter(eq(EMAIL), any())).thenReturn(0L);
        when(resetAttemptRepository.countByAddressAndCreatedAtAfter(eq(IP), any())).thenReturn(10L);

        assertEquals(GENERIC, passwordResetService.forgotPassword(EMAIL, request(IP)));

        verify(userRepository, never()).findByEmail(anyString());
        verify(emailService, never()).sendResetPasswordEmail(anyString(), anyString(), anyString());
        verify(resetAttemptRepository).save(any(PasswordResetAttempt.class));
    }

    @Test
    void unknownEmailReturnsTheSameMessageAndStillCountsTowardTheIpWindow() {
        noRequestsYet();
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());

        String message = passwordResetService.forgotPassword(EMAIL, request(IP));

        assertEquals(GENERIC, message);
        verify(userRepository, never()).save(any(User.class));
        verify(emailService, never()).sendResetPasswordEmail(anyString(), anyString(), anyString());
        verify(resetAttemptRepository).save(any(PasswordResetAttempt.class));
    }

    @Test
    void softDeletedUserReturnsTheSameMessageWithoutIssuingAToken() {
        noRequestsYet();
        User deleted = user();
        deleted.setDeletedAt(LocalDateTime.now().minusDays(1));
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(deleted));

        assertEquals(GENERIC, passwordResetService.forgotPassword(EMAIL, request(IP)));

        assertEquals(GENERIC, passwordResetService.forgotPassword(EMAIL, request(IP)));
        verify(userRepository, never()).save(any(User.class));
        verify(emailService, never()).sendResetPasswordEmail(anyString(), anyString(), anyString());
    }

    @Test
    void blankEmailReturnsTheSameMessageWithoutTouchingAnyRepository() {
        assertEquals(GENERIC, passwordResetService.forgotPassword("   ", request(IP)));
        assertEquals(GENERIC, passwordResetService.forgotPassword(null, request(IP)));

        verify(userRepository, never()).findByEmail(anyString());
        verify(resetAttemptRepository, never()).save(any(PasswordResetAttempt.class));
    }

    @Test
    void emailDeliveryFailureClearsTokenAndKeepsGenericMessage() {
        noRequestsYet();
        User user = user();
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        doThrow(new MailDeliveryException("Không gửi được email. Vui lòng thử lại hoặc liên hệ hỗ trợ."))
                .when(emailService).sendResetPasswordEmail(anyString(), anyString(), anyString());

        assertEquals(GENERIC, passwordResetService.forgotPassword(EMAIL, request(IP)));

        assertNull(user.getResetToken());
        assertNull(user.getResetTokenExpiry());
    }

    @Test
    void resetPasswordClearsTokenSoAFollowingRequestIssuesADifferentOne() {
        noRequestsYet();
        User user = user();
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        passwordResetService.forgotPassword(EMAIL, request(IP));
        String firstToken = user.getResetToken();

        when(userRepository.findByResetToken(firstToken)).thenReturn(Optional.of(user));
        when(passwordEncoder.encode("ProbePass!23")).thenReturn("new-hash");
        PasswordResetResponse reset =
                passwordResetService.resetPassword(firstToken, "ProbePass!23", "ProbePass!23", false);

        assertNotNull(reset.getEventId());
        assertNull(user.getResetToken());
        assertNull(user.getResetTokenExpiry());

        passwordResetService.forgotPassword(EMAIL, request(IP));
        assertNotEquals(firstToken, user.getResetToken());
    }
}
