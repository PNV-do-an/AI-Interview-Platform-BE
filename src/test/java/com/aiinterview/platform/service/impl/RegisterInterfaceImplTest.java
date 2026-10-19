package com.aiinterview.platform.service.impl;

import com.aiinterview.platform.common.exception.EmailAlreadyRegisteredException;
import com.aiinterview.platform.common.exception.InvalidPasswordException;
import com.aiinterview.platform.common.exception.MailDeliveryException;
import com.aiinterview.platform.model.dto.response.RegisterResponse;
import com.aiinterview.platform.model.entity.RegisterAttempt;
import com.aiinterview.platform.model.entity.User;
import com.aiinterview.platform.model.enums.Role;
import com.aiinterview.platform.model.repository.RegisterAttemptRepository;
import com.aiinterview.platform.model.repository.UserRepository;
import com.aiinterview.platform.service.EmailService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RegisterInterfaceImplTest {

    private static final String EMAIL = "new.candidate@example.com";
    private static final String GOOD_PASSWORD = "ProbePass!23";
    private static final String IP = "203.0.113.10";

    @Mock
    private UserRepository userRepository;
    @Mock
    private RegisterAttemptRepository registerAttemptRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private EmailService emailService;
    @Mock
    private HttpServletRequest request;

    @InjectMocks
    private RegisterInterfaceImpl registerService;

    @BeforeEach
    void stubClientIp() {
        when(request.getHeader("X-Forwarded-For")).thenReturn(null);
        when(request.getRemoteAddr()).thenReturn(IP);
    }

    private void noRecentFailures() {
        when(registerAttemptRepository.countByAddressAndTimestampAfter(anyString(), any())).thenReturn(0L);
        when(registerAttemptRepository.countByAccountAndTimestampAfter(anyString(), any())).thenReturn(0L);
    }

    @Test
    void registersDisabledUserAwaitingEmailVerificationWithoutAttemptRow() {
        noRecentFailures();
        when(userRepository.existsByEmail(EMAIL)).thenReturn(false);
        when(passwordEncoder.encode(GOOD_PASSWORD)).thenReturn("hashed");

        RegisterResponse response = registerService.register(EMAIL, GOOD_PASSWORD, "Jane Doe", null, request);

        assertTrue(response.getMessage().contains("kiểm tra email"));
        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        User user = saved.getValue();
        assertEquals(EMAIL, user.getEmail());
        assertEquals(Role.ROLE_USER, user.getRole());
        // Bắt buộc xác thực email: user tạo ra chưa được kích hoạt, chỉ verifyEmail() mới bật lên
        assertFalse(user.isEnabled());
        assertNotNull(user.getVerificationToken());
        verify(registerAttemptRepository, never()).save(any(RegisterAttempt.class));
    }

    @Test
    void duplicateEmailIsRejectedWith409ExceptionAndRecordedAsFailedAttempt() {
        noRecentFailures();
        when(userRepository.existsByEmail(EMAIL)).thenReturn(true);

        assertThrows(EmailAlreadyRegisteredException.class,
                () -> registerService.register(EMAIL, GOOD_PASSWORD, "Jane Doe", null, request));

        verify(userRepository, never()).save(any(User.class));
        verify(emailService, never()).sendVerificationEmail(anyString(), anyString(), anyString());

        ArgumentCaptor<RegisterAttempt> attempt = ArgumentCaptor.forClass(RegisterAttempt.class);
        verify(registerAttemptRepository).save(attempt.capture());
        assertEquals(IP, attempt.getValue().getAddress());
        assertEquals(EMAIL, attempt.getValue().getAccount());
        assertNotNull(attempt.getValue().getTimestamp());
    }

    @Test
    void weakPasswordIsRecordedAsFailedAttempt() {
        noRecentFailures();
        when(userRepository.existsByEmail(EMAIL)).thenReturn(false);

        assertThrows(InvalidPasswordException.class,
                () -> registerService.register(EMAIL, "weakpass", "Jane Doe", null, request));

        ArgumentCaptor<RegisterAttempt> attempt = ArgumentCaptor.forClass(RegisterAttempt.class);
        verify(registerAttemptRepository).save(attempt.capture());
        assertEquals(IP, attempt.getValue().getAddress());
        assertEquals(EMAIL, attempt.getValue().getAccount());
        assertNotNull(attempt.getValue().getTimestamp());
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void emailWithFiveRecentFailuresIsBlockedBeforeAnyCheck() {
        when(registerAttemptRepository.countByAddressAndTimestampAfter(anyString(), any())).thenReturn(0L);
        when(registerAttemptRepository.countByAccountAndTimestampAfter(anyString(), any())).thenReturn(5L);

        InvalidPasswordException ex = assertThrows(InvalidPasswordException.class,
                () -> registerService.register(EMAIL, GOOD_PASSWORD, "Jane Doe", null, request));
        assertTrue(ex.getMessage().contains("5 phút"));
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void ipWithTenRecentFailuresIsBlocked() {
        when(registerAttemptRepository.countByAddressAndTimestampAfter(anyString(), any())).thenReturn(10L);

        InvalidPasswordException ex = assertThrows(InvalidPasswordException.class,
                () -> registerService.register(EMAIL, GOOD_PASSWORD, "Jane Doe", null, request));
        assertTrue(ex.getMessage().contains("bất thường"));
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void emailSendFailureBreaksRegistration() {
        noRecentFailures();
        when(userRepository.existsByEmail(EMAIL)).thenReturn(false);
        when(passwordEncoder.encode(GOOD_PASSWORD)).thenReturn("hashed");
        doThrow(new MailDeliveryException("Không gửi được email. Vui lòng thử lại hoặc liên hệ hỗ trợ."))
                .when(emailService).sendVerificationEmail(anyString(), anyString(), anyString());

        MailDeliveryException ex = assertThrows(MailDeliveryException.class,
                () -> registerService.register(EMAIL, GOOD_PASSWORD, "Jane Doe", null, request));

        assertTrue(ex.getMessage().contains("Không gửi được email"));
        verify(userRepository).save(any(User.class));
    }
}
