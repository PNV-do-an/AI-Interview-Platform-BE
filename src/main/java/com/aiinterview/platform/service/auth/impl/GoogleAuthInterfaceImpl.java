package com.aiinterview.platform.service.auth.impl;

import com.aiinterview.platform.common.exception.InvalidAccountException;
import com.aiinterview.platform.model.entity.User;
import com.aiinterview.platform.model.repository.UserRepository;
import com.aiinterview.platform.service.auth.GoogleAuthInterface;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class GoogleAuthInterfaceImpl implements GoogleAuthInterface {

    private static final String TOKENINFO_URL = "https://oauth2.googleapis.com/tokeninfo?id_token=";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    @Value("${GOOGLE_CLIENT_ID:}")
    private String clientId;

    @Override
    public String getClientId() {
        return clientId == null ? "" : clientId;
    }

    @Override
    public User loginWithGoogle(String idToken, HttpServletRequest request) {
        if (clientId == null || clientId.isBlank()) {
            throw new InvalidAccountException("Đăng nhập Google chưa được cấu hình trên hệ thống");
        }
        if (idToken == null || idToken.isBlank()) {
            throw new InvalidAccountException("Thiếu token Google");
        }

        JsonNode claims = verifyTokenWithGoogle(idToken);

        String aud = claims.path("aud").asText("");
        if (!clientId.equals(aud)) {
            throw new InvalidAccountException("Token Google không thuộc ứng dụng này");
        }
        String iss = claims.path("iss").asText("");
        if (!iss.equals("https://accounts.google.com") && !iss.equals("accounts.google.com")) {
            throw new InvalidAccountException("Nguồn phát hành token không hợp lệ");
        }
        long exp = claims.path("exp").asLong(0);
        if (exp <= Instant.now().getEpochSecond()) {
            throw new InvalidAccountException("Token Google đã hết hạn");
        }

        String email = claims.path("email").asText("");
        if (email.isBlank() || !claims.path("email_verified").asBoolean(false)) {
            throw new InvalidAccountException("Email Google chưa được xác minh");
        }

        User user = userRepository.findByEmail(email).orElseGet(() -> {
            String name = claims.path("name").asText("");
            User created = User.builder()
                    .email(email)
                    .fullName(name.isBlank() ? email.substring(0, email.indexOf('@')) : name)
                    .password(passwordEncoder.encode(UUID.randomUUID().toString()))
                    .enabled(true)
                    .build();
            return userRepository.save(created);
        });

        if (user.getDeletedAt() != null) {
            throw new InvalidAccountException("Tài khoản không tồn tại hoặc đã bị xóa");
        }
        if (user.isLocked()) {
            throw new InvalidAccountException("Tài khoản của bạn đã bị khóa bởi quản trị viên. Vui lòng liên hệ hỗ trợ.");
        }
        return user;
    }

    private JsonNode verifyTokenWithGoogle(String idToken) {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(TOKENINFO_URL + idToken))
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();
            HttpResponse<String> res = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() != 200) {
                throw new InvalidAccountException("Google token không hợp lệ");
            }
            return objectMapper.readTree(res.body());
        } catch (InvalidAccountException e) {
            throw e;
        } catch (Exception e) {
            throw new InvalidAccountException("Không thể xác minh token Google: " + e.getMessage());
        }
    }
}
