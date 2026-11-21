package com.aiinterview.platform.service.auth.impl;

import com.aiinterview.platform.common.exception.InvalidAccountException;
import com.aiinterview.platform.model.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * POST /auth/google (folder 00) — chỉ các nhánh kiểm tra tiền đề.
 * <p>
 * Toàn bộ phần còn lại của luồng nằm sau lời gọi HTTP tới Google tokeninfo, mà {@code HttpClient}
 * được khởi tạo cứng trong class (private final, không có điểm inject). Vì vậy các nhánh aud / iss /
 * exp / email_verified / tự tạo user / tài khoản bị khóa <b>chưa</b> phủ được ở unit test — xem
 * docs/testingAuthfeature.md, mục "Khoảng trống đã biết".
 */
@ExtendWith(MockitoExtension.class)
class GoogleAuthInterfaceImplTest {

    private static final String CLIENT_ID = "google-client-id.apps.googleusercontent.com";

    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordEncoder passwordEncoder;

    private GoogleAuthInterfaceImpl service;
    private HttpServletRequest httpRequest;

    @BeforeEach
    void setUp() {
        service = new GoogleAuthInterfaceImpl(userRepository, passwordEncoder);
        httpRequest = mock(HttpServletRequest.class);
    }

    @Test
    @DisplayName("Chua cau hinh GOOGLE_CLIENT_ID thi getClientId tra chuoi rong de FE an nut")
    void getClientId_whenNotConfigured_returnsEmptyString() {
        ReflectionTestUtils.setField(service, "clientId", null);

        assertThat(service.getClientId()).isEmpty();
    }

    @Test
    @DisplayName("Da cau hinh thi getClientId tra dung gia tri cho FE dung OAuth")
    void getClientId_whenConfigured_returnsValue() {
        ReflectionTestUtils.setField(service, "clientId", CLIENT_ID);

        assertThat(service.getClientId()).isEqualTo(CLIENT_ID);
    }

    @Test
    @DisplayName("Chua cau hinh client id thi tu choi ngay, khong cham database")
    void loginWithGoogle_whenNotConfigured_isRejected() {
        ReflectionTestUtils.setField(service, "clientId", "");

        assertThatThrownBy(() -> service.loginWithGoogle("id-token", httpRequest))
                .isInstanceOf(InvalidAccountException.class)
                .hasMessage("Đăng nhập Google chưa được cấu hình trên hệ thống");
        verifyNoInteractions(userRepository, passwordEncoder);
    }

    @Test
    @DisplayName("Thieu id token thi tu choi truoc khi goi Google")
    void loginWithGoogle_withoutToken_isRejected() {
        ReflectionTestUtils.setField(service, "clientId", CLIENT_ID);

        assertThatThrownBy(() -> service.loginWithGoogle("  ", httpRequest))
                .isInstanceOf(InvalidAccountException.class)
                .hasMessage("Thiếu token Google");
        verifyNoInteractions(userRepository, passwordEncoder);
    }

    @Test
    @DisplayName("Cau hinh thieu duoc bao truoc ca khi token cung thieu")
    void loginWithGoogle_whenNotConfiguredAndTokenMissing_reportsConfigurationFirst() {
        ReflectionTestUtils.setField(service, "clientId", null);

        assertThatThrownBy(() -> service.loginWithGoogle(null, httpRequest))
                .isInstanceOf(InvalidAccountException.class)
                .hasMessage("Đăng nhập Google chưa được cấu hình trên hệ thống");
    }
}
