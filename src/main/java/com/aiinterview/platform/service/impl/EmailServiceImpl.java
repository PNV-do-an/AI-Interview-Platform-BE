package com.aiinterview.platform.service.impl;

import com.aiinterview.platform.common.exception.MailDeliveryException;
import com.aiinterview.platform.service.EmailService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

/**
 * Gửi mail qua Gmail API (HTTPS).
 *
 * SMTP bị loại vì Render free tier chặn outbound port 25/465/587 (changelog 26/09/2025),
 * còn Resend ở chế độ test chỉ giao thư cho email chủ tài khoản nên user thật không nhận được
 * link xác thực. Gmail API chạy trên 443, không cần verify domain, miễn là có OAuth refresh
 * token của chính hộp thư gửi.
 */
@Service
@Slf4j
public class EmailServiceImpl implements EmailService {

    /** Message trả về client: mô tả hậu quả, không lộ chi tiết upstream. */
    static final String SEND_FAILED_MESSAGE =
            "Không gửi được email. Vui lòng thử lại hoặc liên hệ hỗ trợ.";

    /** Access token sống 1 giờ; làm sớm 5 phút để không có request nào rơi vào token hết hạn. */
    private static final long TOKEN_LIVE_SECONDS = 3300;

    private final RestClient http;
    private final String clientId;
    private final String clientSecret;
    private final String refreshToken;
    private final String tokenUri;
    private final String from;
    private final String frontendUrl;

    private volatile String accessToken;
    private volatile long accessTokenExpiresAtEpochSecond;

    public EmailServiceImpl(
            @Value("${app.mail.gmail.client-id:}") String clientId,
            @Value("${app.mail.gmail.client-secret:}") String clientSecret,
            @Value("${app.mail.gmail.refresh-token:}") String refreshToken,
            @Value("${app.mail.from:}") String from,
            @Value("${app.mail.token-uri:https://oauth2.googleapis.com/token}") String tokenUri,
            @Value("${app.mail.api-base-url:https://gmail.googleapis.com}") String apiBaseUrl,
            @Value("${app.frontend-url:http://localhost:3000}") String frontendUrl) {
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.refreshToken = refreshToken;
        this.from = from;
        this.frontendUrl = frontendUrl;
        this.tokenUri = tokenUri;
        this.http = RestClient.builder().baseUrl(apiBaseUrl).build();
    }

    // Ném MailDeliveryException khi thiếu cấu hình hoặc Google từ chối (4xx/5xx) — mail xác thực
    // và reset password là bắt buộc, caller dựa vào exception để hoàn tác hoặc báo lỗi 502.
    private void send(String to, String subject, String text) {
        if (clientId.isBlank() || clientSecret.isBlank() || refreshToken.isBlank() || from.isBlank()) {
            log.error("Thiếu cấu hình Gmail API (GMAIL_CLIENT_ID/GMAIL_CLIENT_SECRET/GMAIL_REFRESH_TOKEN/MAIL_FROM)");
            throw new MailDeliveryException(SEND_FAILED_MESSAGE);
        }
        try {
            http.post()
                    .uri("/gmail/v1/users/me/messages/send")
                    .header("Authorization", "Bearer " + accessToken())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("raw", buildRawMessage(from, to, subject, text)))
                    .retrieve()
                    .toBodilessEntity();
        } catch (MailDeliveryException e) {
            throw e;
        } catch (RestClientException e) {
            throw new MailDeliveryException(SEND_FAILED_MESSAGE, e);
        }
    }

    private String accessToken() {
        long now = System.currentTimeMillis() / 1000;
        if (accessToken != null && now < accessTokenExpiresAtEpochSecond) {
            return accessToken;
        }
        synchronized (this) {
            if (accessToken != null && System.currentTimeMillis() / 1000 < accessTokenExpiresAtEpochSecond) {
                return accessToken;
            }
            MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
            form.add("client_id", clientId);
            form.add("client_secret", clientSecret);
            form.add("refresh_token", refreshToken);
            form.add("grant_type", "refresh_token");
            Map<String, Object> response;
            try {
                response = RestClient.builder().build().post()
                        .uri(tokenUri)
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .body(form)
                        .retrieve()
                        .body(Map.class);
            } catch (RestClientException e) {
                throw new MailDeliveryException(SEND_FAILED_MESSAGE, e);
            }
            if (response == null || !(response.get("access_token") instanceof String token)) {
                throw new MailDeliveryException(SEND_FAILED_MESSAGE);
            }
            accessToken = token;
            accessTokenExpiresAtEpochSecond = System.currentTimeMillis() / 1000 + TOKEN_LIVE_SECONDS;
            return accessToken;
        }
    }

    static String buildRawMessage(String from, String to, String subject, String text) {
        String encodedSubject = Base64.getEncoder().encodeToString(subject.getBytes(StandardCharsets.UTF_8));
        String raw = "From: " + from + "\r\n"
                + "To: " + to + "\r\n"
                + "Subject: =?UTF-8?B?" + encodedSubject + "?=\r\n"
                + "MIME-Version: 1.0\r\n"
                + "Content-Type: text/plain; charset=UTF-8\r\n"
                + "\r\n"
                + text;
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
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
