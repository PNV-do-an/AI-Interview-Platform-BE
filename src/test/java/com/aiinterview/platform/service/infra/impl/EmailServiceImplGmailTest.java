package com.aiinterview.platform.service.infra.impl;

import com.aiinterview.platform.common.exception.MailDeliveryException;
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
 */
class EmailServiceImplGmailTest {

    private HttpServer server;
    private final List<String> rawMessages = new CopyOnWriteArrayList<>();
    private final AtomicInteger tokenCalls = new AtomicInteger();
    private volatile boolean sendShouldFail;

    @BeforeEach
    void startUpstream() throws IOException {
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
        server.stop(0);
    }

    private EmailServiceImpl service() {
        String base = "http://127.0.0.1:" + server.getAddress().getPort();
        return new EmailServiceImpl("client-id", "client-secret", "refresh-token",
                "AI Interview Platform <sender@resend.local>", base + "/token", base,
                "https://aiinterview-vn.vercel.app", false, "", "", Optional.empty());
    }

    @Test
    void verificationEmailIsSentAsBase64UrlRawRfc2822() {
        service().sendVerificationEmail("candidate@example.com", "Jane Doe", "tok-123");

        assertEquals(1, rawMessages.size());
        String decoded = decodeRaw(rawMessages.get(0));
        assertTrue(decoded.contains("From: AI Interview Platform <sender@resend.local>"), decoded);
        assertTrue(decoded.contains("To: candidate@example.com"), decoded);
        assertTrue(decoded.contains("=?UTF-8?B?"), "subject tiếng Việt phải mã hóa RFC 2047");
        assertTrue(decoded.contains("https://aiinterview-vn.vercel.app/verify?token=tok-123"), decoded);
    }

    @Test
    void accessTokenIsCachedAcrossSends() {
        EmailServiceImpl service = service();
        service.sendVerificationEmail("a@example.com", "A", "t1");
        service.sendResetPasswordEmail("b@example.com", "B", "t2");

        assertEquals(2, rawMessages.size());
        assertEquals(1, tokenCalls.get(), "refresh token chỉ đổi lấy access token một lần cho tới khi gần hết hạn");
    }

    @Test
    void upstreamRejectionBecomesMailDeliveryException() {
        sendShouldFail = true;

        MailDeliveryException ex = assertThrows(MailDeliveryException.class,
                () -> service().sendVerificationEmail("a@example.com", "A", "t1"));
        assertEquals("Không gửi được email. Vui lòng thử lại hoặc liên hệ hỗ trợ.", ex.getMessage());
    }

    @Test
    void missingCredentialsFailFastWithoutCallingGoogle() {
        String base = "http://127.0.0.1:" + server.getAddress().getPort();
        EmailServiceImpl unconfigured =
                new EmailServiceImpl("", "", "", "", base + "/token", base, "http://localhost:3000",
                        false, "", "", Optional.empty());

        assertThrows(MailDeliveryException.class,
                () -> unconfigured.sendVerificationEmail("a@example.com", "A", "t1"));
        assertEquals(0, tokenCalls.get());
        assertTrue(rawMessages.isEmpty());
    }

    @Test
    void devLogModeSendsNothingAndDoesNotFailWithoutCredentials() {
        String base = "http://127.0.0.1:" + server.getAddress().getPort();
        // app.mail.dev-log=true: dùng cho local/Postman khi chưa có GMAIL_*.
        EmailServiceImpl devLog =
                new EmailServiceImpl("", "", "", "", base + "/token", base, "http://localhost:3000",
                        true, "", "", Optional.empty());

        devLog.sendVerificationEmail("a@example.com", "A", "tok-dev");
        devLog.sendResetPasswordEmail("b@example.com", "B", "tok-reset");

        assertEquals(0, tokenCalls.get(), "dev-log không được gọi Gmail");
        assertTrue(rawMessages.isEmpty(), "dev-log không được gửi mail thật");
    }

    @Test
    void smtpIsPreferredOverGmailApiWhenAppPasswordConfigured() throws Exception {
        JavaMailSender sender = mock(JavaMailSender.class);
        when(sender.createMimeMessage())
                .thenReturn(new MimeMessage(Session.getInstance(new Properties())));
        EmailServiceImpl service = smtpService(sender, "lenamle799@gmail.com", "sjjynlazsjyqweig");

        service.sendVerificationEmail("candidate@example.com", "Jane Doe", "tok-123");

        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(sender).send(captor.capture());
        MimeMessage sent = captor.getValue();
        assertEquals("Xác thực tài khoản AI Interview Platform", sent.getSubject(),
                "subject tiếng Việt phải được mã hóa UTF-8, không phải mojibake");
        assertTrue(sent.getContent().toString().contains("/verify?token=tok-123"));
        assertEquals(0, tokenCalls.get(), "có SMTP thì không được gọi Gmail API");
        assertTrue(rawMessages.isEmpty());
    }

    @Test
    void smtpRejectionBecomesMailDeliveryException() {
        JavaMailSender sender = mock(JavaMailSender.class);
        when(sender.createMimeMessage())
                .thenReturn(new MimeMessage(Session.getInstance(new Properties())));
        // Gmail trả 535-5.7.8 khi dùng mật khẩu đăng nhập thay vì App Password.
        doThrow(new MailSendException("535-5.7.8 Username and Password not accepted"))
                .when(sender).send(any(MimeMessage.class));
        EmailServiceImpl service = smtpService(sender, "lenamle799@gmail.com", "sai-mat-khau");

        assertThrows(MailDeliveryException.class,
                () -> service.sendVerificationEmail("a@example.com", "A", "t1"));
    }

    private EmailServiceImpl smtpService(JavaMailSender sender, String username, String password) {
        String base = "http://127.0.0.1:" + server.getAddress().getPort();
        return new EmailServiceImpl("", "", "", username, base + "/token", base,
                "http://localhost:3000", false, username, password, Optional.of(sender));
    }

    private static String decodeRaw(String jsonPayload) {
        int start = jsonPayload.indexOf("\"raw\":\"") + "\"raw\":\"".length();
        int end = jsonPayload.indexOf('"', start);
        return new String(Base64.getUrlDecoder().decode(jsonPayload.substring(start, end)),
                StandardCharsets.UTF_8);
    }
}
