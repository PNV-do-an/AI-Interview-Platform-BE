package com.aiinterview.platform.service.auth.impl;

import com.aiinterview.platform.common.exception.MailDeliveryException;
import com.aiinterview.platform.model.dto.response.PasswordResetResponse;
import com.aiinterview.platform.model.entity.PasswordResetAttempt;
import com.aiinterview.platform.model.entity.User;
import com.aiinterview.platform.model.enums.Role;
import com.aiinterview.platform.model.repository.PasswordResetAttemptRepository;
import com.aiinterview.platform.model.repository.UserRepository;
import com.aiinterview.platform.service.auth.impl.PasswordResetServiceImpl;
import com.aiinterview.platform.service.infra.EmailService;
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
 *
 * <p>Mỗi test chia 3 khối comment: <b>Arrange</b> → <b>Act</b> → <b>Assert</b>.</p>
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
        // Arrange: chua ai yeu cau trong cua so, email co user that
        noRequestsYet();
        User user = user();
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));

        // Act
        String message = passwordResetService.forgotPassword(EMAIL, request(IP));

        // Assert: message generic (khong tiet lo email co ton tai), token >= 32 ky tu, han 30 phut
        assertEquals(GENERIC, message);
        assertNotNull(user.getResetToken());
        assertTrue(user.getResetToken().length() >= 32);
        long minutes = Duration.between(LocalDateTime.now(), user.getResetTokenExpiry()).toMinutes();
        assertTrue(minutes > 28 && minutes <= 30, "Token phải có hạn 30 phút, đo được " + minutes);

        // Assert: luu user kem token, gui mail dung nguoi nhan, va ghi attempt theo IP + account
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
        // Arrange: email da dung het 5 luot, IP cung het 5 luot trong cua so
        when(resetAttemptRepository.countByAccountAndCreatedAtAfter(eq(EMAIL), any())).thenReturn(5L);
        when(resetAttemptRepository.countByAddressAndCreatedAtAfter(eq(IP), any())).thenReturn(5L);

        // Act
        String message = passwordResetService.forgotPassword(EMAIL, request(IP));

        // Assert: van tra message generic (bi chan ma khong lo la bi chan), nhung khong doc user,
        // khong cap token, khong gui mail; attempt van duoc ghi de cua so cua IP tiep tuc truot
        assertEquals(GENERIC, message);
        verify(userRepository, never()).findByEmail(anyString());
        verify(userRepository, never()).save(any(User.class));
        verify(emailService, never()).sendResetPasswordEmail(anyString(), anyString(), anyString());
        verify(resetAttemptRepository).save(any(PasswordResetAttempt.class));
    }

    @Test
    void tenthIpRequestStillSendsButEleventhIsThrottledForAFreshEmail() {
        // Arrange: email sach nhung IP da dung 10 luot -> chan theo IP, khong phai theo email
        when(resetAttemptRepository.countByAccountAndCreatedAtAfter(eq(EMAIL), any())).thenReturn(0L);
        when(resetAttemptRepository.countByAddressAndCreatedAtAfter(eq(IP), any())).thenReturn(10L);

        // Act
        assertEquals(GENERIC, passwordResetService.forgotPassword(EMAIL, request(IP)));

        // Assert: chan theo IP thi khong duoc doc user cua email do (tranh do email hang loat)
        verify(userRepository, never()).findByEmail(anyString());
        verify(emailService, never()).sendResetPasswordEmail(anyString(), anyString(), anyString());
        verify(resetAttemptRepository).save(any(PasswordResetAttempt.class));
    }

    @Test
    void unknownEmailReturnsTheSameMessageAndStillCountsTowardTheIpWindow() {
        // Arrange: chua co request nao, email khong ton tai
        noRequestsYet();
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());

        // Act
        String message = passwordResetService.forgotPassword(EMAIL, request(IP));

        // Assert: message y het truong hop thanh cong (khong cho do email qua chenh lech message),
        // nhung khong gui mail; attempt van ghi de IP bi dem dung
        assertEquals(GENERIC, message);
        verify(userRepository, never()).save(any(User.class));
        verify(emailService, never()).sendResetPasswordEmail(anyString(), anyString(), anyString());
        verify(resetAttemptRepository).save(any(PasswordResetAttempt.class));
    }

    @Test
    void softDeletedUserReturnsTheSameMessageWithoutIssuingAToken() {
        // Arrange: email ton tai nhung user da xoa mem
        noRequestsYet();
        User deleted = user();
        deleted.setDeletedAt(LocalDateTime.now().minusDays(1));
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(deleted));

        // Act: goi 2 lan (test goc lam vay) de chac chan hanh vi on dinh
        assertEquals(GENERIC, passwordResetService.forgotPassword(EMAIL, request(IP)));

        // Assert: ca 2 lan deu tra message generic, va tai khoan da xoa khong duoc cap token
        assertEquals(GENERIC, passwordResetService.forgotPassword(EMAIL, request(IP)));
        verify(userRepository, never()).save(any(User.class));
        verify(emailService, never()).sendResetPasswordEmail(anyString(), anyString(), anyString());
    }

    @Test
    void blankEmailReturnsTheSameMessageWithoutTouchingAnyRepository() {
        // Arrange: input vo nghia (khoang trang va null)

        // Act & Assert: van tra message generic de khong ai doan duoc email nao ton tai
        assertEquals(GENERIC, passwordResetService.forgotPassword("   ", request(IP)));
        assertEquals(GENERIC, passwordResetService.forgotPassword(null, request(IP)));

        // Input rong thi khong duoc cham DB (ke ca bang dem attempt)
        verify(userRepository, never()).findByEmail(anyString());
        verify(resetAttemptRepository, never()).save(any(PasswordResetAttempt.class));
    }

    @Test
    void emailDeliveryFailureClearsTokenAndKeepsGenericMessage() {
        // Arrange: user hop le nhung buoc gui mail nem MailDeliveryException
        noRequestsYet();
        User user = user();
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        doThrow(new MailDeliveryException("Không gửi được email. Vui lòng thử lại hoặc liên hệ hỗ trợ."))
                .when(emailService).sendResetPasswordEmail(anyString(), anyString(), anyString());

        // Act
        assertEquals(GENERIC, passwordResetService.forgotPassword(EMAIL, request(IP)));

        // Assert: xoa token vua sinh (neu giu lai thi token "mo coi" con song 30 phut ma
        // khong ai biet), va van khong lo loi mail ra ngoai
        assertNull(user.getResetToken());
        assertNull(user.getResetTokenExpiry());
    }

    @Test
    void resetPasswordClearsTokenSoAFollowingRequestIssuesADifferentOne() {
        // Arrange: user da xin link va dang giu token dau tien
        noRequestsYet();
        User user = user();
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        passwordResetService.forgotPassword(EMAIL, request(IP));
        String firstToken = user.getResetToken();

        // Act: dung token do de dat mat khau moi
        when(userRepository.findByResetToken(firstToken)).thenReturn(Optional.of(user));
        when(passwordEncoder.encode("ProbePass!23")).thenReturn("new-hash");
        PasswordResetResponse reset =
                passwordResetService.resetPassword(firstToken, "ProbePass!23", "ProbePass!23", false);

        // Assert: tra eventId, va token phai bi xoa ngay (token chi dung mot lan)
        assertNotNull(reset.getEventId());
        assertNull(user.getResetToken());
        assertNull(user.getResetTokenExpiry());

        // Assert: lan xin tiep theo phai sinh token KHAC (token cu khong the dung lai)
        passwordResetService.forgotPassword(EMAIL, request(IP));
        assertNotEquals(firstToken, user.getResetToken());
    }
}
