package com.aiinterview.platform.service.infra.impl;

import com.aiinterview.platform.common.exception.MailDeliveryException;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.javamail.JavaMailSender;

import java.util.List;
import java.util.Optional;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Phần còn lại của EmailService mà EmailServiceImplGmailTest (đường Gmail API + SMTP thành công) chưa chạm:
 * <ul>
 *   <li>email BẮT BUỘC (xác thực, reset, khóa tài khoản) phải ném MailDeliveryException khi thiếu cấu hình;</li>
 *   <li>email THÔNG BÁO (ứng tuyển, lịch, nhắc lịch, đơn hàng) phải nuốt lỗi — không được làm fail nghiệp vụ chính;</li>
 *   <li>cấu hình nửa vời (có MAIL_USERNAME, thiếu MAIL_PASSWORD) phải báo đúng biến thiếu.</li>
 * </ul>
 */
class EmailServiceImplNotificationTest {

    private JavaMailSender sender;

    @BeforeEach
    void setUp() {
        sender = mock(JavaMailSender.class);
        when(sender.createMimeMessage()).thenAnswer(
                call -> new MimeMessage(Session.getInstance(new Properties())));
    }

    // ── email bắt buộc: fail fast ───────────────────────────────────────────

    @Test
    @DisplayName("Có MAIL_USERNAME nhưng thiếu MAIL_PASSWORD: báo lỗi gửi mail, không gọi SMTP")
    void usernameWithoutPasswordFailsFast() {
        EmailServiceImpl service = new EmailServiceImpl("", "", "", "", "http://token", "http://api",
                "http://localhost:3000", false, "sender@gmail.com", "", Optional.of(sender));

        assertThatThrownBy(() -> service.sendVerificationEmail("a@example.com", "A", "t1"))
                .isInstanceOf(MailDeliveryException.class)
                .hasMessage(EmailServiceImpl.SEND_FAILED_MESSAGE);
        verifyNoInteractions(sender);
    }

    @Test
    @DisplayName("Không cấu hình gì và dev-log=false: email xác thực ném lỗi để caller báo 502")
    void mandatoryEmailWithoutConfigThrows() {
        EmailServiceImpl service = unconfigured();

        assertThatThrownBy(() -> service.sendResetPasswordEmail("a@example.com", "A", "t1"))
                .isInstanceOf(MailDeliveryException.class);
        assertThatThrownBy(() -> service.sendAccountLockedEmail("a@example.com", "A"))
                .isInstanceOf(MailDeliveryException.class);
    }

    // ── email thông báo: nuốt lỗi ──────────────────────────────────────────

    @Test
    @DisplayName("Email thông báo ứng tuyển/lịch/nhắc lịch không được ném lỗi khi chưa cấu hình")
    void notificationEmailsSwallowDeliveryFailures() {
        EmailServiceImpl service = unconfigured();

        assertThatCode(() -> {
            service.sendApplicationReceivedEmail("hr@example.com", "Jane", "Backend");
            service.sendApplicationStatusEmail("candidate@example.com", "Jane", "Backend", "Đã duyệt");
            service.sendSlotBookedEmail("candidate@example.com", "Jane", "Backend",
                    "20/11/2026 09:00", "Ứng viên");
            service.sendInterviewReminderEmail("candidate@example.com", "Jane", "Backend",
                    "20/11/2026 09:00", "Ứng viên");
            service.sendOrderConfirmationEmail("candidate@example.com", "Jane", "Gói Pro",
                    1_250_000L, "ORD-1");
        }).doesNotThrowAnyException();

        verifyNoInteractions(sender);
    }

    @Test
    @DisplayName("Đơn hàng: số tiền hiển thị kiểu Việt Nam (1.250.000 VND), không phải 1,250,000")
    void orderConfirmationFormatsAmountWithDots() throws Exception {
        EmailServiceImpl service = smtpService("AI Interview <sender@gmail.com>");

        service.sendOrderConfirmationEmail("candidate@example.com", "Jane", "Gói Pro",
                1_250_000L, "ORD-1");

        MimeMessage sent = captureSingle();
        assertThat(sent.getSubject()).isEqualTo("Xác nhận thanh toán đơn hàng ORD-1");
        assertThat(sent.getContent().toString())
                .contains("1.250.000 VND")
                .contains("Gói Pro")
                .contains("ORD-1");
    }

    @Test
    @DisplayName("Email khóa/mở khóa/xóa tài khoản có subject riêng để user phân biệt trong hộp thư")
    void accountLifecycleEmailsHaveDistinctSubjects() throws Exception {
        EmailServiceImpl service = smtpService("AI Interview <sender@gmail.com>");

        service.sendAccountLockedEmail("a@example.com", "Jane");
        service.sendAccountUnlockedEmail("a@example.com", "Jane");
        service.sendAccountDeletedEmail("a@example.com", "Jane");
        service.sendPasswordResetConfirmation("a@example.com", "Jane");

        List<String> subjects = subjectsOf(4);
        assertThat(subjects).containsExactly(
                "Tài khoản của bạn đã bị khóa",
                "Tài khoản của bạn đã được mở khóa",
                "Tài khoản của bạn đã bị xóa",
                "Mật khẩu đã được đặt lại thành công");
    }

    @Test
    @DisplayName("From lấy MAIL_FROM khi có; nếu trống thì dùng luôn MAIL_USERNAME (Gmail chặn From khác)")
    void senderFallsBackToSmtpUsername() throws Exception {
        EmailServiceImpl withFrom = smtpService("Platform <no-reply@aiinterview.vn>");
        withFrom.sendVerificationEmail("a@example.com", "A", "t1");

        EmailServiceImpl withoutFrom = new EmailServiceImpl("", "", "", "", "http://token", "http://api",
                "http://localhost:3000", false, "sender@gmail.com", "app-password", Optional.of(sender));
        withoutFrom.sendVerificationEmail("a@example.com", "A", "t2");

        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(sender, times(2)).send(captor.capture());
        assertThat(captor.getAllValues().get(0).getFrom()[0].toString()).contains("no-reply@aiinterview.vn");
        assertThat(captor.getAllValues().get(1).getFrom()[0].toString()).isEqualTo("sender@gmail.com");
    }

    @Test
    @DisplayName("Link trong mail trỏ đúng route FE và nêu thời hạn của token")
    void linksPointToFrontendRoutes() throws Exception {
        EmailServiceImpl service = smtpService("AI Interview <sender@gmail.com>");

        service.sendVerificationEmail("a@example.com", "A", "tok-verify");
        service.sendResetPasswordEmail("b@example.com", "B", "tok-reset");

        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(sender, times(2)).send(captor.capture());
        String verifyBody = captor.getAllValues().get(0).getContent().toString();
        String resetBody = captor.getAllValues().get(1).getContent().toString();

        assertThat(verifyBody)
                .contains("https://aiinterview-vn.vercel.app/verify?token=tok-verify")
                .contains("24 giờ");
        assertThat(resetBody)
                .contains("https://aiinterview-vn.vercel.app/reset-password?token=tok-reset")
                .contains("30 phút");
    }

    @Test
    @DisplayName("dev-log=true: email bắt buộc cũng không gửi thật và không ném lỗi")
    void devLogNeverTouchesSmtp() {
        EmailServiceImpl service = new EmailServiceImpl("", "", "", "", "http://token", "http://api",
                "http://localhost:3000", true, "sender@gmail.com", "app-password", Optional.of(sender));

        service.sendVerificationEmail("a@example.com", "A", "t1");
        service.sendAccountLockedEmail("a@example.com", "A");

        verify(sender, never()).send(org.mockito.ArgumentMatchers.any(MimeMessage.class));
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private EmailServiceImpl unconfigured() {
        return new EmailServiceImpl("", "", "", "", "http://token", "http://api",
                "http://localhost:3000", false, "", "", Optional.empty());
    }

    private EmailServiceImpl smtpService(String from) {
        return new EmailServiceImpl("", "", "", from, "http://token", "http://api",
                "https://aiinterview-vn.vercel.app", false, "sender@gmail.com", "app-password",
                Optional.of(sender));
    }

    private MimeMessage captureSingle() throws Exception {
        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(sender).send(captor.capture());
        return captor.getValue();
    }

    private List<String> subjectsOf(int expectedSends) throws Exception {
        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(sender, times(expectedSends)).send(captor.capture());
        return captor.getAllValues().stream().map(message -> {
            try {
                return message.getSubject();
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }).toList();
    }
}
