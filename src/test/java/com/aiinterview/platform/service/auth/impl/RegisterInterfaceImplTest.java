package com.aiinterview.platform.service.auth.impl;

import com.aiinterview.platform.common.exception.EmailAlreadyRegisteredException;
import com.aiinterview.platform.common.exception.InvalidPasswordException;
import com.aiinterview.platform.common.exception.MailDeliveryException;
import com.aiinterview.platform.model.dto.response.RegisterResponse;
import com.aiinterview.platform.model.entity.RegisterAttempt;
import com.aiinterview.platform.model.entity.User;
import com.aiinterview.platform.model.enums.Role;
import com.aiinterview.platform.model.repository.RegisterAttemptRepository;
import com.aiinterview.platform.model.repository.UserRepository;
import com.aiinterview.platform.service.auth.impl.RegisterInterfaceImpl;
import com.aiinterview.platform.service.infra.EmailService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

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

/**
 * POST /api/v1/auth/register (folder 00). Hai cua chan rieng biet: theo IP (10 lan / cua so) va
 * theo email (5 lan / cua so). Moi lan that bai deu ghi RegisterAttempt de con dem.
 *
 * <p>Mỗi test chia 3 khối comment: <b>Arrange</b> → <b>Act</b> → <b>Assert</b>.</p>
 */
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
        // Khong co X-Forwarded-For -> lay remoteAddr (dung cho mọi test ben duoi)
        when(request.getHeader("X-Forwarded-For")).thenReturn(null);
        when(request.getRemoteAddr()).thenReturn(IP);
    }

    private void noRecentFailures() {
        when(registerAttemptRepository.countByAddressAndTimestampAfter(anyString(), any())).thenReturn(0L);
        when(registerAttemptRepository.countByAccountAndTimestampAfter(anyString(), any())).thenReturn(0L);
    }

    @Test
    void registersDisabledUserAwaitingEmailVerificationWithoutAttemptRow() {
        // Arrange: cua so dem trong (khong co lan that bai nao), email chua ton tai
        noRecentFailures();
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());
        when(passwordEncoder.encode(GOOD_PASSWORD)).thenReturn("hashed");

        // Act
        RegisterResponse response = registerService.register(EMAIL, GOOD_PASSWORD, "Jane Doe", null, request);

        // Assert: user duoc luu o trang thai chua kich hoat + co verificationToken
        assertTrue(response.getMessage().contains("kiểm tra email"));
        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        User user = saved.getValue();
        assertEquals(EMAIL, user.getEmail());
        assertEquals(Role.ROLE_USER, user.getRole());
        // Bắt buộc xác thực email: user tạo ra chưa được kích hoạt, chỉ verifyEmail() mới bật lên
        assertFalse(user.isEnabled());
        assertNotNull(user.getVerificationToken());

        // Dang ky thanh cong thi khong ghi RegisterAttempt (chi ghi khi that bai)
        verify(registerAttemptRepository, never()).save(any(RegisterAttempt.class));
    }

    @Test
    void duplicateEmailIsRejectedWith409ExceptionAndRecordedAsFailedAttempt() {
        // Arrange: email da co user dang hoat dong
        noRecentFailures();
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(
                User.builder().email(EMAIL).role(Role.ROLE_USER).enabled(true).build()));

        // Act
        EmailAlreadyRegisteredException ex = assertThrows(EmailAlreadyRegisteredException.class,
                () -> registerService.register(EMAIL, GOOD_PASSWORD, "Jane Doe", null, request));

        // Assert: message "da ton tai" (mapper doi thanh 409), khong tao user, khong gui mail
        assertEquals("Email đã tồn tại trong hệ thống", ex.getMessage());
        verify(userRepository, never()).save(any(User.class));
        verify(emailService, never()).sendVerificationEmail(anyString(), anyString(), anyString());

        // Van phai ghi attempt that bai kem dia chi + account de con dem rate-limit
        ArgumentCaptor<RegisterAttempt> attempt = ArgumentCaptor.forClass(RegisterAttempt.class);
        verify(registerAttemptRepository).save(attempt.capture());
        assertEquals(IP, attempt.getValue().getAddress());
        assertEquals(EMAIL, attempt.getValue().getAccount());
        assertNotNull(attempt.getValue().getTimestamp());
    }

    /**
     * User bấm đăng ký lại vì tưởng chưa có tài khoản: message phải tự nói lối thoát (kiểm tra
     * Spam / bấm gửi lại link), không phải chuỗi cụt đẩy họ vào thế bí.
     */
    @Test
    void duplicateUnverifiedEmailPointsToTheVerificationLinkInsteadOfADeadEnd() {
        // Arrange: email da ton tai nhung chua xac thuc (enabled=false)
        noRecentFailures();
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(
                User.builder().email(EMAIL).role(Role.ROLE_USER).enabled(false)
                        .verificationToken("tok-live").build()));

        // Act
        EmailAlreadyRegisteredException ex = assertThrows(EmailAlreadyRegisteredException.class,
                () -> registerService.register(EMAIL, GOOD_PASSWORD, "Jane Doe", null, request));

        // Assert: message phai goi y duoc 3 loi thoat (chua xac thuc / Spam / gui lai link),
        // neu chi bao "email da ton tai" thi nguoi dung bi ket
        assertTrue(ex.getMessage().contains("chưa xác thực"), ex.getMessage());
        assertTrue(ex.getMessage().contains("Spam"), ex.getMessage());
        assertTrue(ex.getMessage().contains("Gửi lại link xác thực"), ex.getMessage());
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void weakPasswordIsRecordedAsFailedAttempt() {
        // Arrange: email hop le nhung mat khau khong dat chinh sach
        noRecentFailures();
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());

        // Act
        assertThrows(InvalidPasswordException.class,
                () -> registerService.register(EMAIL, "weakpass", "Jane Doe", null, request));

        // Assert: ghi attempt that bai (de dem), khong tao user
        ArgumentCaptor<RegisterAttempt> attempt = ArgumentCaptor.forClass(RegisterAttempt.class);
        verify(registerAttemptRepository).save(attempt.capture());
        assertEquals(IP, attempt.getValue().getAddress());
        assertEquals(EMAIL, attempt.getValue().getAccount());
        assertNotNull(attempt.getValue().getTimestamp());
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void emailWithFiveRecentFailuresIsBlockedBeforeAnyCheck() {
        // Arrange: email nay da that bai 5 lan trong cua so (IP van sach)
        when(registerAttemptRepository.countByAddressAndTimestampAfter(anyString(), any())).thenReturn(0L);
        when(registerAttemptRepository.countByAccountAndTimestampAfter(anyString(), any())).thenReturn(5L);

        // Act
        InvalidPasswordException ex = assertThrows(InvalidPasswordException.class,
                () -> registerService.register(EMAIL, GOOD_PASSWORD, "Jane Doe", null, request));

        // Assert: bi chan truoc khi kiem tra mat khau/email, message noi ro phai doi 5 phut
        assertTrue(ex.getMessage().contains("5 phút"));
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void ipWithTenRecentFailuresIsBlocked() {
        // Arrange: IP da that bai 10 lan (nguong chan thiet bi)
        when(registerAttemptRepository.countByAddressAndTimestampAfter(anyString(), any())).thenReturn(10L);

        // Act
        InvalidPasswordException ex = assertThrows(InvalidPasswordException.class,
                () -> registerService.register(EMAIL, GOOD_PASSWORD, "Jane Doe", null, request));

        // Assert: message "bat thuong" danh cho nguoi dung bi chan do IP (khac message theo email)
        assertTrue(ex.getMessage().contains("bất thường"));
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void emailSendFailureBreaksRegistration() {
        // Arrange: moi thu hop le, chi co buoc gui mail nem MailDeliveryException
        noRecentFailures();
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());
        when(passwordEncoder.encode(GOOD_PASSWORD)).thenReturn("hashed");
        doThrow(new MailDeliveryException("Không gửi được email. Vui lòng thử lại hoặc liên hệ hỗ trợ."))
                .when(emailService).sendVerificationEmail(anyString(), anyString(), anyString());

        // Act
        MailDeliveryException ex = assertThrows(MailDeliveryException.class,
                () -> registerService.register(EMAIL, GOOD_PASSWORD, "Jane Doe", null, request));

        // Assert: khong nuot loi mail (nguoi dung se khong bao gio nhan duoc link neu im lang),
        // va user van da duoc luu truoc do nen lan sau ho gui lai link duoc
        assertTrue(ex.getMessage().contains("Không gửi được email"));
        verify(userRepository).save(any(User.class));
    }
}
