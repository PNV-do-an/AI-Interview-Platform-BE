package com.aiinterview.platform.service.auth.impl;

import com.aiinterview.platform.common.exception.InvalidAccountException;
import com.aiinterview.platform.model.dto.response.SsoConfigResponse;
import com.aiinterview.platform.model.entity.User;
import com.aiinterview.platform.model.repository.UserRepository;
import com.aiinterview.platform.service.auth.SsoAuthInterface;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SsoAuthInterfaceImpl implements SsoAuthInterface {

    private static final String SCOPES = "openid email profile";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final ObjectMapper objectMapper = new ObjectMapper();
    /** Bean từ HttpClientConfig — điểm tiêm để test các nhánh sau lời gọi HTTP. */
    private final HttpClient httpClient;

    @Value("${SSO_OIDC_ISSUER:}")
    private String issuer;

    /**
     * Đường dẫn IdP tính từ backend (vd http://keycloak:8080/realms/ai-interview khi IdP chạy
     * trong compose). Để trống = dùng SSO_OIDC_ISSUER. Issuer claim vẫn validate theo
     * SSO_OIDC_ISSUER — đây là trường hợp IdP có public URL khác nội bộ (reverse proxy).
     */
    @Value("${SSO_OIDC_BASE_URL:}")
    private String internalBase;

    @Value("${SSO_CLIENT_ID:}")
    private String clientId;

    @Value("${SSO_CLIENT_SECRET:}")
    private String clientSecret;

    @Value("${SSO_ALLOWED_EMAIL_DOMAINS:}")
    private String allowedEmailDomains;

    @Value("${SSO_REDIRECT_URI:http://localhost:3000/login}")
    private String defaultRedirectUri;

    /** Discovery doc chỉ phụ thuộc issuer nên cache lại; IdP đổi endpoint thì cần restart backend. */
    private volatile OidcMetadata cachedMetadata;

    @Override
    public SsoConfigResponse getPublicConfig() {
        if (!isConfigured()) {
            return SsoConfigResponse.builder()
                    .enabled(false).authorizationEndpoint("").clientId("").scopes(SCOPES).build();
        }
        OidcMetadata md = discover();
        return SsoConfigResponse.builder()
                .enabled(true)
                .authorizationEndpoint(md.authorizationEndpoint())
                .clientId(clientId)
                .scopes(SCOPES)
                .build();
    }

    @Override
    public User loginWithSso(String code, String redirectUri, String nonce) {
        if (!isConfigured()) {
            throw new InvalidAccountException("Enterprise SSO chưa được cấu hình trên hệ thống");
        }
        if (code == null || code.isBlank()) {
            throw new InvalidAccountException("Thiếu mã ủy quyền SSO");
        }

        OidcMetadata md = discover();
        String callback = (redirectUri == null || redirectUri.isBlank()) ? defaultRedirectUri : redirectUri;
        JsonNode tokens = exchangeCode(code, callback, toInternalUrl(md.tokenEndpoint()));

        String idToken = tokens.path("id_token").asText("");
        String accessToken = tokens.path("access_token").asText("");
        if (idToken.isBlank()) {
            throw new InvalidAccountException("IdP không trả về id_token");
        }

        Jwt claims = verifyIdToken(idToken, toInternalUrl(md.jwksUri()));

        if (!claims.getAudience().contains(clientId)) {
            throw new InvalidAccountException("id_token không dành cho ứng dụng này");
        }
        if (nonce != null && !nonce.isBlank() && !nonce.equals(claims.getClaimAsString("nonce"))) {
            throw new InvalidAccountException("Nonce không khớp, vui lòng đăng nhập lại");
        }

        String email = claims.getClaimAsString("email");
        boolean verified = Boolean.TRUE.equals(claims.getClaim("email_verified"));
        if ((email == null || email.isBlank() || !verified) && !md.userinfoEndpoint().isBlank()) {
            JsonNode userinfo = fetchUserinfo(accessToken, toInternalUrl(md.userinfoEndpoint()));
            String uiEmail = userinfo.path("email").asText("");
            if (!uiEmail.isBlank() && userinfo.path("email_verified").asBoolean(false)) {
                email = uiEmail;
                verified = true;
            }
        }
        if (email == null || email.isBlank() || !verified) {
            throw new InvalidAccountException("IdP không cung cấp email đã xác minh");
        }
        if (!isEmailDomainAllowed(email, allowedEmailDomains)) {
            throw new InvalidAccountException("Email không thuộc domain công ty được phép dùng SSO");
        }

        final String finalEmail = email.toLowerCase(Locale.ROOT);
        User user = userRepository.findByEmail(finalEmail).orElseGet(() -> {
            String name = claims.getClaimAsString("name");
            if (name == null || name.isBlank()) {
                name = finalEmail.substring(0, finalEmail.indexOf('@'));
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

    private boolean isConfigured() {
        return issuer != null && !issuer.isBlank()
                && clientId != null && !clientId.isBlank()
                && clientSecret != null && !clientSecret.isBlank();
    }

    private String normalizedIssuer() {
        return stripTrailingSlash(issuer);
    }

    private String discoveryBase() {
        return stripTrailingSlash(internalBase == null || internalBase.isBlank() ? issuer : internalBase);
    }

    /**
     * Discovery doc luôn mô tả URL public của IdP. Khi backend đi tới IdP qua một host khác
     * (network nội bộ), phải viết lại prefix public -> internal cho các call của chính backend;
     * frontend vẫn nhận URL public để trình duyệt tự gọi.
     */
    private String toInternalUrl(String url) {
        if (internalBase == null || internalBase.isBlank() || url == null || url.isBlank()) {
            return url;
        }
        String publicPrefix = normalizedIssuer();
        if (url.startsWith(publicPrefix)) {
            return stripTrailingSlash(internalBase) + url.substring(publicPrefix.length());
        }
        return url;
    }

    private static String stripTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    private OidcMetadata discover() {
        OidcMetadata md = cachedMetadata;
        if (md != null) {
            return md;
        }
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(discoveryBase() + "/.well-known/openid-configuration"))
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();
            HttpResponse<String> res = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() != 200) {
                throw new InvalidAccountException("IdP trả lỗi khi đọc OIDC discovery: HTTP " + res.statusCode());
            }
            JsonNode j = objectMapper.readTree(res.body());
            OidcMetadata parsed = new OidcMetadata(
                    j.path("authorization_endpoint").asText(""),
                    j.path("token_endpoint").asText(""),
                    j.path("jwks_uri").asText(""),
                    j.path("userinfo_endpoint").asText(""));
            if (parsed.authorizationEndpoint().isBlank()
                    || parsed.tokenEndpoint().isBlank()
                    || parsed.jwksUri().isBlank()) {
                throw new InvalidAccountException("OIDC discovery của IdP thiếu endpoint bắt buộc");
            }
            cachedMetadata = parsed;
            return parsed;
        } catch (InvalidAccountException e) {
            throw e;
        } catch (Exception e) {
            throw new InvalidAccountException("Không kết nối được IdP: " + describe(e));
        }
    }

    private JsonNode exchangeCode(String code, String redirectUri, String tokenEndpoint) {
        String form = "grant_type=authorization_code"
                + "&code=" + enc(code)
                + "&redirect_uri=" + enc(redirectUri)
                + "&client_id=" + enc(clientId)
                + "&client_secret=" + enc(clientSecret);
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(tokenEndpoint))
                    .timeout(Duration.ofSeconds(15))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(form, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> res = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            JsonNode body = objectMapper.readTree(res.body());
            if (res.statusCode() != 200 || body.hasNonNull("error")) {
                String desc = body.path("error_description").asText(body.path("error").asText("unknown_error"));
                throw new InvalidAccountException("IdP từ chối mã ủy quyền: " + desc);
            }
            return body;
        } catch (InvalidAccountException e) {
            throw e;
        } catch (Exception e) {
            throw new InvalidAccountException("Không đổi được mã ủy quyền SSO: " + describe(e));
        }
    }

    private Jwt verifyIdToken(String idToken, String jwksUri) {
        try {
            NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwksUri)
                    .jwsAlgorithm(SignatureAlgorithm.RS256)
                    .build();
            decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(normalizedIssuer()));
            return decoder.decode(idToken);
        } catch (Exception e) {
            throw new InvalidAccountException("id_token của IdP không hợp lệ: " + e.getMessage());
        }
    }

    private JsonNode fetchUserinfo(String accessToken, String userinfoEndpoint) {
        if (accessToken == null || accessToken.isBlank()) {
            return objectMapper.createObjectNode();
        }
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(userinfoEndpoint))
                    .timeout(Duration.ofSeconds(10))
                    .header("Authorization", "Bearer " + accessToken)
                    .header("Accept", "application/json")
                    .GET()
                    .build();
            HttpResponse<String> res = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() != 200) {
                return objectMapper.createObjectNode();
            }
            return objectMapper.readTree(res.body());
        } catch (Exception e) {
            return objectMapper.createObjectNode();
        }
    }

    /** allowedDomains rỗng = chấp nhận mọi email đã verify; ngược lại email phải khớp đúng một domain. */
    static boolean isEmailDomainAllowed(String email, String allowedDomainsRaw) {
        if (allowedDomainsRaw == null || allowedDomainsRaw.isBlank()) {
            return true;
        }
        if (email == null) {
            return false;
        }
        int at = email.lastIndexOf('@');
        if (at < 0 || at == email.length() - 1) {
            return false;
        }
        String domain = email.substring(at + 1).toLowerCase(Locale.ROOT);
        for (String raw : allowedDomainsRaw.split(",")) {
            String allowed = raw.trim().toLowerCase(Locale.ROOT);
            if (!allowed.isEmpty() && allowed.equals(domain)) {
                return true;
            }
        }
        return false;
    }

    private static String enc(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    /** ConnectException/UnknownHostException đôi khi message null — in tên exception vẫn đủ để debug. */
    private static String describe(Exception e) {
        String msg = e.getMessage();
        return (msg == null || msg.isBlank()) ? e.getClass().getSimpleName() : msg;
    }

    private record OidcMetadata(String authorizationEndpoint, String tokenEndpoint,
                                String jwksUri, String userinfoEndpoint) {
    }
}
