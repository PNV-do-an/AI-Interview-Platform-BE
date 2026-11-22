package com.aiinterview.platform.service.auth.impl;

import com.aiinterview.platform.common.exception.InvalidAccountException;
import com.aiinterview.platform.model.dto.response.SsoConfigResponse;
import com.aiinterview.platform.model.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * POST /auth/sso + GET /auth/sso/config (folder 00, enterprise OIDC). Covered here: configuration
 * gate, discovery parsing/caching, and the token exchange. Deliberately not covered: anything after a
 * successful token exchange that needs to verify the id_token signature, because NimbusJwtDecoder
 * fetches the JWKS over the network on its own (no injection point) — that branch stays a documented
 * gap and belongs to an integration test with a real IdP or a mocked JWKS endpoint.
 *
 * <p>Mỗi test chia 3 khối comment: <b>Arrange</b> → <b>Act</b> → <b>Assert</b>. Test một lời gọi thì
 * gộp Act &amp; Assert.</p>
 */
@ExtendWith(MockitoExtension.class)
class SsoAuthInterfaceImplTest {

    private static final String ISSUER = "https://idp.example.com/realms/ai";
    private static final String CLIENT_ID = "ai-interview-portal";
    private static final String CLIENT_SECRET = "sso-secret";
    private static final String DISCOVERY = "{\"authorization_endpoint\":\"" + ISSUER + "/authorize\","
            + "\"token_endpoint\":\"" + ISSUER + "/token\","
            + "\"jwks_uri\":\"" + ISSUER + "/jwks\","
            + "\"userinfo_endpoint\":\"" + ISSUER + "/userinfo\"}";

    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordEncoder passwordEncoder;

    private StubHttpClient http;
    private SsoAuthInterfaceImpl service;

    @BeforeEach
    void setUp() {
        http = new StubHttpClient();
        service = new SsoAuthInterfaceImpl(userRepository, passwordEncoder, http);
        ReflectionTestUtils.setField(service, "issuer", ISSUER);
        ReflectionTestUtils.setField(service, "clientId", CLIENT_ID);
        ReflectionTestUtils.setField(service, "clientSecret", CLIENT_SECRET);
        ReflectionTestUtils.setField(service, "allowedEmailDomains", "");
        ReflectionTestUtils.setField(service, "defaultRedirectUri", "http://localhost:3000/login");
    }

    @Test
    @DisplayName("Chua cau hinh du issuer/client id/secret thi FE nhan enabled=false")
    void getPublicConfig_whenNotConfigured_isDisabled() {
        // Arrange: thieu secret = cau hinh chua day du (du issuer va client id van co)
        ReflectionTestUtils.setField(service, "clientSecret", "");

        // Act
        SsoConfigResponse config = service.getPublicConfig();

        // Assert: FE dua vao enabled de an nut SSO; scopes van tra mac dinh de FE khong phai
        // xu ly null, va khong duoc goi IdP khi tinh nang da tat
        assertThat(config.isEnabled()).isFalse();
        assertThat(config.getClientId()).isEmpty();
        assertThat(config.getAuthorizationEndpoint()).isEmpty();
        assertThat(config.getScopes()).isEqualTo("openid email profile");
        assertThat(http.requestUris()).isEmpty();
    }

    @Test
    @DisplayName("Da cau hinh thi tra authorization_endpoint doc tu discovery")
    void getPublicConfig_whenConfigured_returnsDiscoveryEndpoint() {
        // Arrange: IdP tra discovery document hop le
        http.enqueue(200, DISCOVERY);

        // Act
        SsoConfigResponse config = service.getPublicConfig();

        // Assert: endpoint tra ve FE la cua IdP (khong phai cua minh), va discovery duoc goi dung
        // duong dan chuan /.well-known/openid-configuration
        assertThat(config.isEnabled()).isTrue();
        assertThat(config.getClientId()).isEqualTo(CLIENT_ID);
        assertThat(config.getAuthorizationEndpoint()).isEqualTo(ISSUER + "/authorize");
        assertThat(http.requestUris())
                .containsExactly(ISSUER + "/.well-known/openid-configuration");
    }

    @Test
    @DisplayName("Discovery chi goi mot lan: ket qua duoc cache trong service")
    void getPublicConfig_cachesDiscoveryDocument() {
        // Arrange: chi co mot response trong hang doi
        http.enqueue(200, DISCOVERY);

        // Act: goi 2 lan lien tiep
        service.getPublicConfig();
        service.getPublicConfig();

        // Assert: lan 2 dung cache, neu khong cache thi lan 2 se nem IOException vi het response
        assertThat(http.requestUris()).hasSize(1);
    }

    @Test
    @DisplayName("IdP tra status khac 200 khi doc discovery thi bao ro status")
    void discovery_whenIdpReturnsError_isReported() {
        // Arrange: IdP dang loi 503
        http.enqueue(503, "");

        // Act & Assert: message phai kem status de biet loi phia IdP hay phia minh
        assertThatThrownBy(service::getPublicConfig)
                .isInstanceOf(InvalidAccountException.class)
                .hasMessage("IdP trả lỗi khi đọc OIDC discovery: HTTP 503");
    }

    @Test
    @DisplayName("Discovery thieu endpoint bat buoc thi tu choi cau hinh")
    void discovery_whenEndpointsMissing_isRejected() {
        // Arrange: discovery chi co authorization_endpoint, thieu token/jwks/userinfo
        http.enqueue(200, "{\"authorization_endpoint\":\"" + ISSUER + "/authorize\"}");

        // Act & Assert: thieu endpoint thi khong the exchange code, phai tu choi ngay
        assertThatThrownBy(service::getPublicConfig)
                .isInstanceOf(InvalidAccountException.class)
                .hasMessage("OIDC discovery của IdP thiếu endpoint bắt buộc");
    }

    @Test
    @DisplayName("Khong ket noi duoc IdP thi message kem nguyen nhan")
    void discovery_whenNetworkFails_isWrapped() {
        // Arrange: loi DNS ngay o loi goi discovery
        http.failNextWith(new IOException("unknown host"));

        // Act & Assert: kem nguyen nhan goc de phan biet sai cau hinh mang voi IdP tra loi
        assertThatThrownBy(service::getPublicConfig)
                .isInstanceOf(InvalidAccountException.class)
                .hasMessage("Không kết nối được IdP: unknown host");
    }

    @Test
    @DisplayName("Chua cau hinh SSO thi login bi tu choi truoc khi goi IdP")
    void loginWithSso_whenNotConfigured_isRejected() {
        // Arrange: thieu issuer
        ReflectionTestUtils.setField(service, "issuer", null);

        // Act & Assert: message rieng cho tinh nang chua bat, khong phai loi mang
        assertThatThrownBy(() -> service.loginWithSso("code", null, null))
                .isInstanceOf(InvalidAccountException.class)
                .hasMessage("Enterprise SSO chưa được cấu hình trên hệ thống");

        assertThat(http.requestUris()).isEmpty();
        verifyNoInteractions(userRepository);
    }

    @Test
    @DisplayName("Thieu code uy quyen thi tu choi truoc khi goi IdP")
    void loginWithSso_withoutCode_isRejected() {
        // Arrange: FE gui chuoi toan khoang trang

        // Act & Assert: chan o cua, khong ton cong goi discovery + token endpoint
        assertThatThrownBy(() -> service.loginWithSso("  ", null, null))
                .isInstanceOf(InvalidAccountException.class)
                .hasMessage("Thiếu mã ủy quyền SSO");

        assertThat(http.requestUris()).isEmpty();
    }

    @Test
    @DisplayName("IdP tu choi code: message lay error_description")
    void loginWithSso_codeRejected_isReported() {
        // Arrange: discovery OK, nhung token endpoint tra 400
        http.enqueue(200, DISCOVERY)
                .enqueue(400, "{\"error\":\"invalid_grant\",\"error_description\":\"code expired\"}");

        // Act & Assert: dung nguyen van error_description de con doi chieu log cua IdP
        assertThatThrownBy(() -> service.loginWithSso("expired-code", null, null))
                .isInstanceOf(InvalidAccountException.class)
                .hasMessage("IdP từ chối mã ủy quyền: code expired");

        // Thu tu goi: discovery truoc, token sau
        assertThat(http.requestUris()).hasSize(2);
        assertThat(http.requestUris().get(1)).isEqualTo(ISSUER + "/token");
        verifyNoInteractions(userRepository);
    }

    @Test
    @DisplayName("IdP khong tra id_token thi khong the xac thuc nguoi dung")
    void loginWithSso_missingIdToken_isRejected() {
        // Arrange: doi code thanh cong nhung response chi co access_token
        http.enqueue(200, DISCOVERY).enqueue(200, "{\"access_token\":\"opaque\"}");

        // Act & Assert: khong co id_token thi khong biet nguoi dung la ai -> tu choi, khong tao user
        assertThatThrownBy(() -> service.loginWithSso("code", null, null))
                .isInstanceOf(InvalidAccountException.class)
                .hasMessage("IdP không trả về id_token");

        verifyNoInteractions(userRepository);
    }
}
