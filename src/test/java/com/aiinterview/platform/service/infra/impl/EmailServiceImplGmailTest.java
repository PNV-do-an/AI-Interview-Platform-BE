package com.aiinterview.platform.service.infra.impl;

import com.aiinterview.platform.common.exception.MailDeliveryException;
import com.aiinterview.platform.service.infra.impl.EmailServiceImpl;
import com.sun.net.httpserver.HttpServer;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Hai đường gửi mail đều phải có test thật vì đều là wire-level: SMTP (Gmail App Password)
 * là đường chính, Gmail API (HTTPS) là dự phòng cho môi trường chặn outbound 587.
 * Ở đây upstream Gmail API được thay bằng HttpServer nội bộ để kiểm chứng raw message thật sự
 * được dựng đúng, không chỉ verify mock.
 *
 * <p>Mỗi test chia 3 khối comment: <b>Arrange</b> → <b>Act</b> → <b>Assert</b>.</p>
 */
class EmailServiceImplGmailTest {

    private HttpServer server;
    private final List<String> rawMessages = new CopyOnWriteArrayList<>();
    private final AtomicInteger tokenCalls = new AtomicInteger();
    private volatile boolean sendShouldFail;

    @BeforeEach
    void startUpstream() throws IOException {
        // Arrange dùng chung: dựng Gmail API giả (token + messages/send) trên cổng ngẫu nhiên
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/token", exchange -> {
            tokenCalls.incrementAndGet();
            byte[] body = "{\"access_token\":\"tok-test\",\"expires_in\":3600,\"token_type\":\"Bearer\"}"
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.createContext("/gmail/v1/users/me/messages/send", exchange -> {
            String auth = exchange.getRequestHeaders().getFirst("Authorization");
            if (sendShouldFail) {
                byte[] err = "{\"error\":{\"code\":403,\"message\":\"Sending revoked\"}}".getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(403, err.length);
                exchange.getResponseBody().write(err);
                exchange.close();
                return;
            }
            assertEquals("Bearer tok-test", auth, "phải gửi kèm access token vừa refresh");
            String payload = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            rawMessages.add(payload);
            byte[] ok = "{\"id\":\"msg-1\",\"labelIds\":[\"SENT\"]}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, ok.length);
            exchange.getResponseBody().write(ok);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void shutDown() {
        // Dọn server sau mỗi test để cổng không bị giữ lại
        server.stop(0);
    }

    /** Service trỏ Gmail API về server giả; SMTP chưa cấu hình nên mọi lời gọi đi qua HTTPS. */
    private EmailServiceImpl service() {
        String base = "http://127.0.0.1:" + server.getAddress().getPort();
        return new EmailServiceImpl("client-id", "client-secret", "refresh-token",
                "AI Interview Platform <sender@resend.local>", base + "/token", base,
                "https://aiinterview-vn.vercel.app", false, "", "", Optional.empty());
    }

    @Test
    void verificationEmailIsSentAsBase64UrlRawRfc2822() {
        // Act: gửi mail xác thực qua Gmail API giả
        service().sendVerificationEmail("candidate@example.com", "Jane Doe", "tok-123");

        // Assert: đúng 1 request và payload là raw RFC 2822 được bọc base64url
        assertEquals(1, rawMessages.size());
        String decoded = decodeRaw(rawMessages.get(0));
        // Assert: header + liên kết xác thực đúng, subject tiếng Việt được mã hoá RFC 2047
        assertTrue(decoded.contains("From: AI Interview Platform <sender@resend.local>"), decoded);
        assertTrue(decoded.contains("To: candidate@example.com"), decoded);
        assertTrue(decoded.contains("=?UTF-8?B?"), "subject tiếng Việt phải mã hóa RFC 2047");
        assertTrue(decoded.contains("https://aiinterview-vn.vercel.app/verify?token=tok-123"), decoded);
    }

    @Test
    void accessTokenIsCachedAcrossSends() {
        // Arrange: dùng chung một instance service — cache token nằm trong instance
        EmailServiceImpl service = service();

        // Act: gửi 2 mail liên tiếp
        service.sendVerificationEmail("a@example.com", "A", "t1");
        service.sendResetPasswordEmail("b@example.com", "B", "t2");

        // Assert: 2 mail đi, nhưng chỉ 1 lần đổi refresh token lấy access token
        assertEquals(2, rawMessages.size());
        assertEquals(1, tokenCalls.get(), "refresh token chỉ đổi lấy access token một lần cho tới khi gần hết hạn");
    }

    @Test
    void upstreamRejectionBecomesMailDeliveryException() {
        // Arrange: Gmail API giả trả 403 thay vì 200
        sendShouldFail = true;

        // Act & Assert: lỗi upstream được đổi thành MailDeliveryException có message cho FE hiển thị
        MailDeliveryException ex = assertThrows(MailDeliveryException.class,
                () -> service().sendVerificationEmail("a@example.com", "A", "t1"));
        assertEquals("Không gửi được email. Vui lòng thử lại hoặc liên hệ hỗ trợ.", ex.getMessage());
    }

    @Test
    void missingCredentialsFailFastWithoutCallingGoogle() {
        // Arrange: service không có client id / secret / refresh token
        String base = "http://127.0.0.1:" + server.getAddress().getPort();
        EmailServiceImpl unconfigured =
                new EmailServiceImpl("", "", "", "", base + "/token", base, "http://localhost:3000",
                        false, "", "", Optional.empty());

        // Act & Assert: báo lỗi ngay, không đi ra ngoài mạng
        assertThrows(MailDeliveryException.class,
                () -> unconfigured.sendVerificationEmail("a@example.com", "A", "t1"));

        // Assert: 0 lời gọi /token và không có mail nào được gửi
        assertEquals(0, tokenCalls.get());
        assertTrue(rawMessages.isEmpty());
    }

    @Test
    void devLogModeSendsNothingAndDoesNotFailWithoutCredentials() {
        String base = "http://127.0.0.1:" + server.getAddress().getPort();
        // Arrange: bật app.mail.dev-log=true, dùng cho local/Postman khi chưa có GMAIL_*
        EmailServiceImpl devLog =
                new EmailServiceImpl("", "", "", "", base + "/token", base, "http://localhost:3000",
                        true, "", "", Optional.empty());

        // Act: gửi 2 mail trong chế độ dev-log
        devLog.sendVerificationEmail("a@example.com", "A", "tok-dev");
        devLog.sendResetPasswordEmail("b@example.com", "B", "tok-reset");

        // Assert: không gọi Gmail, không gửi mail thật, nhưng API không ném lỗi
        assertEquals(0, tokenCalls.get(), "dev-log không được gọi Gmail");
        assertTrue(rawMessages.isEmpty(), "dev-log không được gửi mail thật");
    }

    @Test
    void smtpIsPreferredOverGmailApiWhenAppPasswordConfigured() throws Exception {
        // Arrange: có username + App Password nên SMTP là đường chính, Gmail API chỉ là dự phòng
        JavaMailSender sender = mock(JavaMailSender.class);
        when(sender.createMimeMessage())
                .thenReturn(new MimeMessage(Session.getInstance(new Properties())));
        // Arrange: có username + App Password (giả lập, không phải credential thật)
        EmailServiceImpl service = smtpService(sender, "smtp-test@example.com", "apppasswordfake1");

        // Act
        service.sendVerificationEmail("candidate@example.com", "Jane Doe", "tok-123");

        // Assert: đúng 1 mail qua SMTP, nội dung có liên kết xác thực
        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(sender).send(captor.capture());
        MimeMessage sent = captor.getValue();
        assertEquals("Xác thực tài khoản AI Interview Platform", sent.getSubject(),
                "subject tiếng Việt phải được mã hóa UTF-8, không phải mojibake");
        assertTrue(sent.getContent().toString().contains("/verify?token=tok-123"));
        // Assert: đã gửi được qua SMTP thì không fallback sang Gmail API
        assertEquals(0, tokenCalls.get(), "có SMTP thì không được gọi Gmail API");
        assertTrue(rawMessages.isEmpty());
    }

    @Test
    void smtpRejectionBecomesMailDeliveryException() {
        // Arrange: SMTP từ chối xác thực (mật khẩu thường thay vì App Password)
        JavaMailSender sender = mock(JavaMailSender.class);
        when(sender.createMimeMessage())
                .thenReturn(new MimeMessage(Session.getInstance(new Properties())));
        // Arrange: Gmail trả 535-5.7.8 khi dùng mật khẩu đăng nhập thay vì App Password
        doThrow(new MailSendException("535-5.7.8 Username and Password not accepted"))
                .when(sender).send(any(MimeMessage.class));
        EmailServiceImpl service = smtpService(sender, "smtp-test@example.com", "sai-mat-khau");

        // Act & Assert: lỗi SMTP cũng thành MailDeliveryException như đường Gmail API
        assertThrows(MailDeliveryException.class,
                () -> service.sendVerificationEmail("a@example.com", "A", "t1"));
    }

    /** Service cấu hình SMTP (username + App Password); Gmail API vẫn trỏ server giả làm dự phòng. */
    private EmailServiceImpl smtpService(JavaMailSender sender, String username, String password) {
        String base = "http://127.0.0.1:" + server.getAddress().getPort();
        return new EmailServiceImpl("", "", "", username, base + "/token", base,
                "http://localhost:3000", false, username, password, Optional.of(sender));
    }

    /** Bóc payload: lấy chuỗi base64url trong trường "raw" rồi giải mã về RFC 2822 dạng text. */
    private static String decodeRaw(String jsonPayload) {
        int start = jsonPayload.indexOf("\"raw\":\"") + "\"raw\":\"".length();
        int end = jsonPayload.indexOf('"', start);
        return new String(Base64.getUrlDecoder().decode(jsonPayload.substring(start, end)),
                StandardCharsets.UTF_8);
    }
}
