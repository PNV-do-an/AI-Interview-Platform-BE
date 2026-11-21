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
 * POST /auth/github (folder 00) — chỉ các nhánh kiểm tra tiền đề.
 * <p>
 * Giống Google: ba lời gọi HTTP (đổi code lấy token, lấy profile, lấy email) dùng {@code HttpClient}
 * khởi tạo cứng trong class nên các nhánh phía sau — GitHub từ chối code, profile không có email đã
 * xác minh, tự tạo user, tài khoản bị khóa — chưa phủ được ở unit test. Xem docs/testingAuthfeature.md.
 */
@ExtendWith(MockitoExtension.class)
class GithubAuthInterfaceImplTest {

    private static final String CLIENT_ID = "gh-client-id";
    private static final String CLIENT_SECRET = "gh-client-secret";

    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordEncoder passwordEncoder;

    private GithubAuthInterfaceImpl service;
    private HttpServletRequest httpRequest;

    @BeforeEach
    void setUp() {
        service = new GithubAuthInterfaceImpl(userRepository, passwordEncoder);
        httpRequest = mock(HttpServletRequest.class);
    }

    @Test
    @DisplayName("Chua cau hinh GITHUB_CLIENT_ID thi getClientId tra chuoi rong")
    void getClientId_whenNotConfigured_returnsEmptyString() {
        ReflectionTestUtils.setField(service, "clientId", null);

        assertThat(service.getClientId()).isEmpty();
    }

    @Test
    @DisplayName("Da cau hinh thi getClientId tra dung gia tri")
    void getClientId_whenConfigured_returnsValue() {
        ReflectionTestUtils.setField(service, "clientId", CLIENT_ID);

        assertThat(service.getClientId()).isEqualTo(CLIENT_ID);
    }

    @Test
    @DisplayName("Thieu client id thi tu choi, khong cham database")
    void loginWithGithub_withoutClientId_isRejected() {
        ReflectionTestUtils.setField(service, "clientId", "");
        ReflectionTestUtils.setField(service, "clientSecret", CLIENT_SECRET);

        assertThatThrownBy(() -> service.loginWithGithub("code", "http://localhost:3000/login", httpRequest))
                .isInstanceOf(InvalidAccountException.class)
                .hasMessage("Đăng nhập GitHub chưa được cấu hình trên hệ thống");
        verifyNoInteractions(userRepository, passwordEncoder);
    }

    @Test
    @DisplayName("Co client id nhung thieu client secret van la cau hinh chua day du")
    void loginWithGithub_withoutClientSecret_isRejected() {
        ReflectionTestUtils.setField(service, "clientId", CLIENT_ID);
        ReflectionTestUtils.setField(service, "clientSecret", "  ");

        assertThatThrownBy(() -> service.loginWithGithub("code", null, httpRequest))
                .isInstanceOf(InvalidAccountException.class)
                .hasMessage("Đăng nhập GitHub chưa được cấu hình trên hệ thống");
        verifyNoInteractions(userRepository);
    }

    @Test
    @DisplayName("Cau hinh day du nhung thieu code uy quyen thi tu choi truoc khi goi GitHub")
    void loginWithGithub_withoutCode_isRejected() {
        ReflectionTestUtils.setField(service, "clientId", CLIENT_ID);
        ReflectionTestUtils.setField(service, "clientSecret", CLIENT_SECRET);

        assertThatThrownBy(() -> service.loginWithGithub(" ", "http://localhost:3000/login", httpRequest))
                .isInstanceOf(InvalidAccountException.class)
                .hasMessage("Thiếu mã ủy quyền GitHub");
        verifyNoInteractions(userRepository, passwordEncoder);
    }
}
