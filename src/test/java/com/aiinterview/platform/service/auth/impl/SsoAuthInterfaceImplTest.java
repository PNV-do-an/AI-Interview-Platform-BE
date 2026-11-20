package com.aiinterview.platform.service.auth.impl;

import com.aiinterview.platform.common.exception.InvalidAccountException;
import com.aiinterview.platform.model.dto.response.SsoConfigResponse;
import com.aiinterview.platform.model.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Enterprise SSO (OIDC) — chỉ test được phần "trước khi ra mạng": cấu hình thiếu, thiếu mã uỷ quyền,
 * và IdP không truy cập được.
 * <p>
 * Giống Google/GitHub OAuth, {@code HttpClient} ở đây được tạo ngay trong field (không inject) nên
 * không thể giả lập phản hồi của IdP. Muốn test luồng đầy đủ (exchange code, verify id_token, map
 * user) phải chạy với một Keycloak thật — xem TwoFactorIntegrationTest cho ví dụ dùng Testcontainers.
 * <p>
 * Phần logic thuần tuý còn lại — allowlist domain — đã có SsoEmailDomainPolicyTest.
 */
class SsoAuthInterfaceImplTest {

    /** Issuer trỏ vào cổng 1 của localhost: mọi kết nối bị từ chối ngay, test không cần mạng. */
    private static final String ISSUER = "http://127.0.0.1:1/realms/ai-interview";

    private UserRepository userRepository;
    private PasswordEncoder passwordEncoder;
    private SsoAuthInterfaceImpl service;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);
        service = new SsoAuthInterfaceImpl(userRepository, passwordEncoder);
    }

    @Test
    @DisplayName("Chưa cấu hình: /sso/config trả enabled=false để FE ẩn nút SSO")
    void publicConfig_disabledWhenNotConfigured() {
        SsoConfigResponse config = service.getPublicConfig();

        assertThat(config.isEnabled()).isFalse();
        assertThat(config.getAuthorizationEndpoint()).isEmpty();
        assertThat(config.getClientId()).isEmpty();
        assertThat(config.getScopes()).isEqualTo("openid email profile");
        verifyNoInteractions(userRepository);
    }

    @Test
    @DisplayName("Chưa cấu hình: POST /sso bị từ chối rõ ràng, không cố gọi IdP")
    void login_rejectedWhenNotConfigured() {
        assertThatThrownBy(() -> service.loginWithSso("code", null, null))
                .isInstanceOf(InvalidAccountException.class)
                .hasMessage("Enterprise SSO chưa được cấu hình trên hệ thống");

        verifyNoInteractions(userRepository, passwordEncoder);
    }

    @Test
    @DisplayName("Thiếu client secret cũng coi như chưa cấu hình (không thể exchange code)")
    void login_rejectedWhenClientSecretMissing() {
        configure(ISSUER, "ai-interview-web", "  ");

        assertThatThrownBy(() -> service.loginWithSso("code", null, null))
                .isInstanceOf(InvalidAccountException.class)
                .hasMessage("Enterprise SSO chưa được cấu hình trên hệ thống");
    }

    @Test
    @DisplayName("Đã cấu hình nhưng thiếu code: 401 'Thiếu mã ủy quyền SSO', chưa chạm DB")
    void login_blankCodeRejected() {
        configure(ISSUER, "ai-interview-web", "secret");

        assertThatThrownBy(() -> service.loginWithSso("   ", null, null))
                .isInstanceOf(InvalidAccountException.class)
                .hasMessage("Thiếu mã ủy quyền SSO");
        assertThatThrownBy(() -> service.loginWithSso(null, null, null))
                .isInstanceOf(InvalidAccountException.class)
                .hasMessage("Thiếu mã ủy quyền SSO");

        verifyNoInteractions(userRepository);
    }

    @Test
    @DisplayName("IdP không truy cập được: báo 'Không kết nối được IdP', không lộ stacktrace")
    void login_unreachableIdpIsWrapped() {
        configure(ISSUER, "ai-interview-web", "secret");

        assertThatThrownBy(() -> service.loginWithSso("code", null, null))
                .isInstanceOf(InvalidAccountException.class)
                .hasMessageStartingWith("Không kết nối được IdP:");

        verifyNoInteractions(userRepository, passwordEncoder);
    }

    @Test
    @DisplayName("Hiện tại /sso/config cũng ném 401 khi IdP chết (hành vi đang có, ghi lại để biết)")
    void publicConfig_unreachableIdpThrowsInsteadOfFallingBackToDisabled() {
        configure(ISSUER, "ai-interview-web", "secret");

        // Khác với trường hợp chưa cấu hình: đã cấu hình thì phải discovery, IdP chết -> ném lỗi.
        // FE vì thế thấy nút SSO lỗi thay vì tự ẩn. Nếu muốn "fail soft" thì đây là chỗ phải sửa.
        assertThatThrownBy(() -> service.getPublicConfig())
                .isInstanceOf(InvalidAccountException.class)
                .hasMessageStartingWith("Không kết nối được IdP:");
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private void configure(String issuer, String clientId, String clientSecret) {
        ReflectionTestUtils.setField(service, "issuer", issuer);
        ReflectionTestUtils.setField(service, "clientId", clientId);
        ReflectionTestUtils.setField(service, "clientSecret", clientSecret);
    }
}
