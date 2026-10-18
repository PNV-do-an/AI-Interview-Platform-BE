package com.aiinterview.platform.service.impl;

import com.aiinterview.platform.service.EmailService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.Map;

@Service
public class EmailServiceImpl implements EmailService {

    private final RestClient resend;
    private final String apiKey;
    private final String from;
    private final String frontendUrl;

    public EmailServiceImpl(
            @Value("${app.mail.resend-api-key:}") String apiKey,
            @Value("${app.mail.from:onboarding@resend.dev}") String from,
            @Value("${app.mail.resend-base-url:https://api.resend.com}") String baseUrl,
            @Value("${app.frontend-url:http://localhost:3000}") String frontendUrl) {
        this.apiKey = apiKey;
        this.from = from;
        this.frontendUrl = frontendUrl;
        this.resend = RestClient.builder().baseUrl(baseUrl).build();
    }

    // Ném exception khi lỗi (thiếu key, 4xx/5xx) — semantics như JavaMailSender cũ:
    // mail verify bắt buộc phải bắn lên để caller quyết định; notification mail tự nuốt bên dưới.
    private void send(String to, String subject, String text) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("Chưa cấu hình RESEND_API_KEY - email không được gửi tới " + to);
        }
        resend.post()
                .uri("/emails")
                .header("Authorization", "Bearer " + apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("from", from, "to", new String[]{to}, "subject", subject, "text", text))
                .retrieve()
                .toBodilessEntity();
    }

    @Override
    public void sendVerificationEmail(String to, String fullName, String token) {
        send(to, "Xác thực tài khoản AI Interview Platform",
                "Xin chào " + fullName + ",\n\n"
                        + "Vui lòng xác thực tài khoản bằng cách nhấn vào link sau:\n"
                        + frontendUrl + "/verify?token=" + token + "\n\n"
                        + "Link có hiệu lực trong 24 giờ.");
    }

    @Override
    public void sendResendVerificationEmail(String to, String fullName, String token) {
        send(to, "Gửi lại xác thực tài khoản AI Interview Platform",
                "Xin chào " + fullName + ",\n\n"
                        + "Đây là link xác thực mới của bạn:\n"
                        + frontendUrl + "/verify?token=" + token + "\n\n"
                        + "Link có hiệu lực trong 24 giờ.");
    }

    @Override
    public void sendResetPasswordEmail(String to, String fullName, String token) {
        send(to, "Đặt lại mật khẩu AI Interview Platform",
                "Xin chào " + fullName + ",\n\n"
                        + "Bạn đã yêu cầu đặt lại mật khẩu. Nhấn vào link sau:\n"
                        + frontendUrl + "/reset-password?token=" + token + "\n\n"
                        + "Link có hiệu lực trong 1 giờ. Nếu bạn không yêu cầu điều này, hãy bỏ qua email này.");
    }

    @Override
    public void sendPasswordResetConfirmation(String to, String fullName) {
        send(to, "Mật khẩu đã được đặt lại thành công",
                "Xin chào " + fullName + ",\n\n"
                        + "Mật khẩu của bạn đã được đặt lại thành công.\n"
                        + "Nếu bạn không thực hiện điều này, vui lòng liên hệ ngay với chúng tôi.");
    }

    @Override
    public void sendAccountLockedEmail(String to, String fullName) {
        send(to, "Tài khoản của bạn đã bị khóa",
                "Xin chào " + fullName + ",\n\n"
                        + "Tài khoản của bạn trên AI Interview Platform đã bị khóa bởi quản trị viên.\n"
                        + "Vui lòng liên hệ hỗ trợ để biết thêm thông tin.");
    }

    @Override
    public void sendAccountUnlockedEmail(String to, String fullName) {
        send(to, "Tài khoản của bạn đã được mở khóa",
                "Xin chào " + fullName + ",\n\n"
                        + "Tài khoản của bạn trên AI Interview Platform đã được quản trị viên mở khóa.\n"
                        + "Bây giờ bạn có thể đăng nhập lại bình thường.");
    }

    @Override
    public void sendAccountDeletedEmail(String to, String fullName) {
        send(to, "Tài khoản của bạn đã bị xóa",
                "Xin chào " + fullName + ",\n\n"
                        + "Tài khoản của bạn trên AI Interview Platform đã bị xóa bởi quản trị viên.\n"
                        + "Vui lòng liên hệ hỗ trợ nếu bạn có thắc mắc.");
    }

    @Override
    public void sendApplicationReceivedEmail(String to, String candidateName, String jobTitle) {
        try {
            send(to, "Có ứng viên ứng tuyển: " + jobTitle,
                    "Xin chào,\n\n"
                            + "Ứng viên " + candidateName + " vừa ứng tuyển vào vị trí \"" + jobTitle + "\".\n"
                            + "Vui lòng đăng nhập hệ thống để xem chi tiết và sắp xếp lịch phỏng vấn.");
        } catch (Exception e) {
            // notification email must not break the main flow
        }
    }

    @Override
    public void sendApplicationStatusEmail(String to, String candidateName, String jobTitle, String statusLabel) {
        try {
            send(to, "Cập nhật trạng thái ứng tuyển: " + jobTitle,
                    "Xin chào " + candidateName + ",\n\n"
                            + "Đơn ứng tuyển của bạn vào vị trí \"" + jobTitle + "\" đã chuyển sang trạng thái: " + statusLabel + ".\n"
                            + "Vui lòng đăng nhập hệ thống để xem chi tiết.");
        } catch (Exception e) {
            // notification email must not break the main flow
        }
    }

    @Override
    public void sendSlotBookedEmail(String to, String name, String jobTitle, String startTime, String role) {
        try {
            send(to, "Xác nhận lịch phỏng vấn: " + jobTitle,
                    "Xin chào " + name + ",\n\n"
                            + "Lịch phỏng vấn cho vị trí \"" + jobTitle + "\" vào lúc " + startTime
                            + " đã được xác nhận (" + role + ").\n"
                            + "Vui lòng tham gia đúng giờ.");
        } catch (Exception e) {
            // notification email must not break the main flow
        }
    }

    @Override
    public void sendOrderConfirmationEmail(String to, String fullName, String packageName, Long amount, String orderCode) {
        try {
            String amountText = String.format("%,d", amount).replace(',', '.');
            send(to, "Xác nhận thanh toán đơn hàng " + orderCode,
                    "Xin chào " + fullName + ",\n\n"
                            + "Thanh toán của bạn đã thành công.\n"
                            + "Mã đơn hàng: " + orderCode + "\n"
                            + "Gói dịch vụ: " + packageName + "\n"
                            + "Số tiền: " + amountText + " VND\n\n"
                            + "Cảm ơn bạn đã sử dụng AI Interview Platform!");
        } catch (Exception e) {
            // notification email must not break the main flow
        }
    }
}
