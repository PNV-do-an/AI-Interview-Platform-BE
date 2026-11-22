package com.aiinterview.platform.service.auth.impl;

import com.aiinterview.platform.common.exception.InvalidAccountException;
import com.aiinterview.platform.model.entity.User;
import com.aiinterview.platform.model.repository.UserRepository;
import com.aiinterview.platform.service.auth.GithubAuthInterface;
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
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class GithubAuthInterfaceImpl implements GithubAuthInterface {

    private static final String TOKEN_URL = "https://github.com/login/oauth/access_token";
    private static final String USER_URL = "https://api.github.com/user";
    private static final String EMAILS_URL = "https://api.github.com/user/emails";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final ObjectMapper objectMapper = new ObjectMapper();
    /** Bean từ HttpClientConfig — điểm tiêm để test các nhánh sau lời gọi HTTP. */
    private final HttpClient httpClient;

    @Value("${GITHUB_CLIENT_ID:}")
    private String clientId;

    @Value("${GITHUB_CLIENT_SECRET:}")
    private String clientSecret;

    @Override
    public String getClientId() {
        return clientId == null ? "" : clientId;
    }

    @Override
    public User loginWithGithub(String code, String redirectUri, HttpServletRequest request) {
        if (clientId == null || clientId.isBlank() || clientSecret == null || clientSecret.isBlank()) {
            throw new InvalidAccountException("Đăng nhập GitHub chưa được cấu hình trên hệ thống");
        }
        if (code == null || code.isBlank()) {
            throw new InvalidAccountException("Thiếu mã ủy quyền GitHub");
        }

        String token = exchangeCodeForToken(code, redirectUri);
        JsonNode profile = fetchProfile(token);

        String login = profile.path("login").asText("");
        String email = profile.path("email").asText("");
        if (email.isBlank()) {
            email = fetchPrimaryEmail(token);
        }
        if (email.isBlank()) {
            throw new InvalidAccountException("GitHub tài khoản chưa có email đã xác minh");
        }

        final String finalEmail = email;
        User user = userRepository.findByEmail(finalEmail).orElseGet(() -> {
            String name = profile.path("name").asText("");
            if (name.isBlank()) {
                name = login.isBlank() ? finalEmail.substring(0, finalEmail.indexOf('@')) : login;
            }
            User created = User.builder()
                    .email(finalEmail)
                    .fullName(name)
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

    private String exchangeCodeForToken(String code, String redirectUri) {
        try {
            String resolvedRedirect = (redirectUri == null || redirectUri.isBlank())
                    ? "http://localhost:3000/login" : redirectUri;
            String body = objectMapper.writeValueAsString(java.util.Map.of(
                    "client_id", clientId,
                    "client_secret", clientSecret,
                    "code", code,
                    "redirect_uri", resolvedRedirect));
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(TOKEN_URL))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> res = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            JsonNode json = objectMapper.readTree(res.body());
            String token = json.path("access_token").asText("");
            if (res.statusCode() != 200 || token.isBlank()) {
                String err = json.path("error_description").asText(json.path("error").asText("unknown"));
                throw new InvalidAccountException("GitHub từ chối mã ủy quyền: " + err);
            }
            return token;
        } catch (InvalidAccountException e) {
            throw e;
        } catch (Exception e) {
            throw new InvalidAccountException("Không thể trao đổi mã GitHub: " + e.getMessage());
        }
    }

    private JsonNode fetchProfile(String accessToken) {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(USER_URL))
                    .timeout(Duration.ofSeconds(10))
                    .header("Authorization", "Bearer " + accessToken)
                    .header("Accept", "application/vnd.github+json")
                    .header("User-Agent", "ai-interview-platform")
                    .GET()
                    .build();
            HttpResponse<String> res = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() != 200) {
                throw new InvalidAccountException("Không thể lấy hồ sơ GitHub (status " + res.statusCode() + ")");
            }
            return objectMapper.readTree(res.body());
        } catch (InvalidAccountException e) {
            throw e;
        } catch (Exception e) {
            throw new InvalidAccountException("Lỗi gọi API GitHub: " + e.getMessage());
        }
    }

    private String fetchPrimaryEmail(String accessToken) {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(EMAILS_URL))
                    .timeout(Duration.ofSeconds(10))
                    .header("Authorization", "Bearer " + accessToken)
                    .header("Accept", "application/vnd.github+json")
                    .header("User-Agent", "ai-interview-platform")
                    .GET()
                    .build();
            HttpResponse<String> res = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() != 200) {
                return "";
            }
            JsonNode arr = objectMapper.readTree(res.body());
            String fallback = "";
            for (JsonNode e : arr) {
                if (e.path("verified").asBoolean(false)) {
                    if (e.path("primary").asBoolean(false)) {
                        return e.path("email").asText("");
                    }
                    if (fallback.isBlank()) {
                        fallback = e.path("email").asText("");
                    }
                }
            }
            return fallback;
        } catch (Exception e) {
            return "";
        }
    }
}
