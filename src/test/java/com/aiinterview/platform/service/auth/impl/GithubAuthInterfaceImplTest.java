package com.aiinterview.platform.service.auth.impl;

import com.aiinterview.platform.common.exception.InvalidAccountException;
import com.aiinterview.platform.model.entity.User;
import com.aiinterview.platform.model.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * POST /auth/github (folder 00). Three sequential HTTP calls and the order matters: exchange code for
 * a token, read /user, and only when /user has no email read /user/emails. The email fallback walks
 * the list twice — primary+verified wins, otherwise the first verified one.
 *
 * <p>Mỗi test chia 3 khối comment: <b>Arrange</b> (dựng gì) → <b>Act</b> (gọi gì) → <b>Assert</b>
 * (khẳng định gì). Test một lời gọi thì gộp Act &amp; Assert.</p>
 */
@ExtendWith(MockitoExtension.class)
class GithubAuthInterfaceImplTest {

    private static final String CLIENT_ID = "gh-client-id";
    private static final String CLIENT_SECRET = "gh-client-secret";
    private static final String EMAIL = "octo@example.com";
    private static final String REDIRECT = "http://localhost:3000/login";

    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordEncoder passwordEncoder;

    private StubHttpClient http;
    private GithubAuthInterfaceImpl service;
    private HttpServletRequest httpRequest;

    @BeforeEach
    void setUp() {
        http = new StubHttpClient();
        service = new GithubAuthInterfaceImpl(userRepository, passwordEncoder, http);
        ReflectionTestUtils.setField(service, "clientId", CLIENT_ID);
        ReflectionTestUtils.setField(service, "clientSecret", CLIENT_SECRET);
        httpRequest = mock(HttpServletRequest.class);
    }

    @Test
    @DisplayName("Chua cau hinh GITHUB_CLIENT_ID thi getClientId tra chuoi rong")
    void getClientId_whenNotConfigured_returnsEmptyString() {
        // Arrange: gia lap ung dung chua bat GitHub login
        ReflectionTestUtils.setField(service, "clientId", null);

        // Act & Assert: FE dua vao chuoi rong de an nut dang nhap GitHub
        assertThat(service.getClientId()).isEmpty();
    }

    @Test
    @DisplayName("Da cau hinh thi getClientId tra dung gia tri")
    void getClientId_whenConfigured_returnsValue() {
        // Arrange: client id da duoc set trong @BeforeEach

        // Act & Assert: tra nguyen van gia tri cau hinh
        assertThat(service.getClientId()).isEqualTo(CLIENT_ID);
    }

    @Test
    @DisplayName("Thieu client id thi tu choi, khong goi GitHub")
    void loginWithGithub_withoutClientId_isRejected() {
        // Arrange: cau hinh rong = chua bat tinh nang
        ReflectionTestUtils.setField(service, "clientId", "");

        // Act & Assert: loi cau hinh, message rieng de FE biet khong phai loi cua nguoi dung
        assertThatThrownBy(() -> service.loginWithGithub("code", REDIRECT, httpRequest))
                .isInstanceOf(InvalidAccountException.class)
                .hasMessage("Đăng nhập GitHub chưa được cấu hình trên hệ thống");

        assertThat(http.requestUris()).isEmpty();
        verifyNoInteractions(userRepository, passwordEncoder);
    }

    @Test
    @DisplayName("Co client id nhung thieu client secret van la cau hinh chua day du")
    void loginWithGithub_withoutClientSecret_isRejected() {
        // Arrange: secret chi con khoang trang (hay gap khi copy .env)
        ReflectionTestUtils.setField(service, "clientSecret", "  ");

        // Act & Assert: van la "chua cau hinh", khong duoc goi GitHub roi that bai mo ho
        assertThatThrownBy(() -> service.loginWithGithub("code", null, httpRequest))
                .isInstanceOf(InvalidAccountException.class)
                .hasMessage("Đăng nhập GitHub chưa được cấu hình trên hệ thống");

        assertThat(http.requestUris()).isEmpty();
    }

    @Test
    @DisplayName("Cau hinh day du nhung thieu code uy quyen thi tu choi truoc khi goi GitHub")
    void loginWithGithub_withoutCode_isRejected() {
        // Arrange: FE gui chuoi toan khoang trang

        // Act & Assert: message danh rieng cho thieu code
        assertThatThrownBy(() -> service.loginWithGithub(" ", REDIRECT, httpRequest))
                .isInstanceOf(InvalidAccountException.class)
                .hasMessage("Thiếu mã ủy quyền GitHub");

        assertThat(http.requestUris()).isEmpty();
        verifyNoInteractions(userRepository);
    }

    @Test
    @DisplayName("GitHub tu choi code: message lay error_description cua GitHub")
    void loginWithGithub_codeRejected_isReported() {
        // Arrange: GitHub tra 200 kem error (khong phai 4xx) va error_description
        http.enqueue(200, "{\"error\":\"bad_verification_code\","
                + "\"error_description\":\"The code passed is incorrect or expired.\"}");

        // Act & Assert: uu tien error_description, giu nguyen van de doi chieu log GitHub
        assertThatThrownBy(() -> service.loginWithGithub("expired-code", REDIRECT, httpRequest))
                .isInstanceOf(InvalidAccountException.class)
                .hasMessage("GitHub từ chối mã ủy quyền: The code passed is incorrect or expired.");

        // Chi 1 loi goi: khong duoc di tiep sang /user khi da bi tu choi
        assertThat(http.requestUris()).hasSize(1);
        verifyNoInteractions(userRepository);
    }

    @Test
    @DisplayName("Response doi token khong co access_token thi coi nhu bi tu choi")
    void loginWithGithub_tokenResponseWithoutAccessToken_isRejected() {
        // Arrange: 200 nhung body rong {}
        http.enqueue(200, "{}");

        // Act & Assert: thieu token cung la bi tu choi; khi khong co ca error thi hien "unknown"
        assertThatThrownBy(() -> service.loginWithGithub("code", REDIRECT, httpRequest))
                .isInstanceOf(InvalidAccountException.class)
                .hasMessage("GitHub từ chối mã ủy quyền: unknown");
    }

    @Test
    @DisplayName("Khong lay duoc ho so GitHub thi message kem status de debug")
    void loginWithGithub_profileCallFails_isReported() {
        // Arrange: doi token OK, nhung /user tra 500
        http.enqueue(200, token("gh-token")).enqueue(500, "{}");

        // Act & Assert: message rieng cho /user, kem status (khac han nhom "tu choi code")
        assertThatThrownBy(() -> service.loginWithGithub("code", REDIRECT, httpRequest))
                .isInstanceOf(InvalidAccountException.class)
                .hasMessage("Không thể lấy hồ sơ GitHub (status 500)");
    }

    @Test
    @DisplayName("Loi mang khi goi API GitHub duoc bao lai, khong lo exception goc")
    void loginWithGithub_profileNetworkFailure_isWrapped() {
        // Arrange: doi token thanh cong, roi loi mang o loi goi /user
        // (StubHttpClient xu ly tuan tu: response truoc, loi sau)
        http.enqueue(200, token("gh-token"));
        http.failNextWith(new IOException("connect timed out"));

        // Act & Assert: message cua nhanh /user, khong phai message cua nhanh doi token
        assertThatThrownBy(() -> service.loginWithGithub("code", REDIRECT, httpRequest))
                .isInstanceOf(InvalidAccountException.class)
                .hasMessage("Lỗi gọi API GitHub: connect timed out");
    }

    @Test
    @DisplayName("Profile co email: chi goi 2 endpoint, dung email do")
    void loginWithGithub_profileEmail_isUsedDirectly() {
        // Arrange: /user da co email nen khong can /user/emails; user da ton tai trong DB
        User existing = User.builder().id(4L).email(EMAIL).fullName("Octo").enabled(true).build();
        http.enqueue(200, token("gh-token")).enqueue(200, profile(EMAIL, "octocat", "Octo Cat"));
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(existing));

        // Act
        User result = service.loginWithGithub("code", null, httpRequest);

        // Assert: dung 2 loi goi theo dung thu tu, va tra dung user cu
        assertThat(result).isSameAs(existing);
        assertThat(http.requestUris()).containsExactly(
                "https://github.com/login/oauth/access_token",
                "https://api.github.com/user");
    }

    @Test
    @DisplayName("Profile khong co email: lay email primary da xac minh tu /user/emails")
    void loginWithGithub_missingProfileEmail_usesPrimaryVerifiedEmail() {
        // Arrange: /user khong co email; /user/emails co 1 email thuong + 1 email primary verified
        User existing = User.builder().id(4L).email(EMAIL).fullName("Octo").enabled(true).build();
        http.enqueue(200, token("gh-token"))
                .enqueue(200, profile("", "octocat", ""))
                .enqueue(200, "[{\"email\":\"old@example.com\",\"primary\":false,\"verified\":true},"
                        + "{\"email\":\"" + EMAIL + "\",\"primary\":true,\"verified\":true}]");
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(existing));

        // Act
        User result = service.loginWithGithub("code", REDIRECT, httpRequest);

        // Assert: chon email primary chu khong phai email verified dau tien, va dung 3 loi goi
        assertThat(result).isSameAs(existing);
        assertThat(http.requestUris()).hasSize(3);
        assertThat(http.requestUris().get(2)).isEqualTo("https://api.github.com/user/emails");
    }

    @Test
    @DisplayName("Khong co email primary thi lay email da xac minh dau tien")
    void loginWithGithub_noPrimary_usesFirstVerifiedEmail() {
        // Arrange: email dau tien chua verify (bi bo qua), email thu hai verified
        http.enqueue(200, token("gh-token"))
                .enqueue(200, profile("", "octocat", ""))
                .enqueue(200, "[{\"email\":\"spam@example.com\",\"primary\":false,\"verified\":false},"
                        + "{\"email\":\"" + EMAIL + "\",\"primary\":false,\"verified\":true}]");
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(
                User.builder().id(4L).email(EMAIL).fullName("Octo").enabled(true).build()));

        // Act
        User result = service.loginWithGithub("code", REDIRECT, httpRequest);

        // Assert: dung email verified (khong dung email chua verify), nen tim thay user theo EMAIL
        assertThat(result.getEmail()).isEqualTo(EMAIL);
    }

    @Test
    @DisplayName("Moi email deu chua xac minh thi khong cho dang nhap")
    void loginWithGithub_noVerifiedEmail_isRejected() {
        // Arrange: chi co mot email va verified=false
        http.enqueue(200, token("gh-token"))
                .enqueue(200, profile("", "octocat", ""))
                .enqueue(200, "[{\"email\":\"spam@example.com\",\"primary\":true,\"verified\":false}]");

        // Act & Assert: khong co email verified thi khong co dinh danh dang tin -> tu choi
        assertThatThrownBy(() -> service.loginWithGithub("code", REDIRECT, httpRequest))
                .isInstanceOf(InvalidAccountException.class)
                .hasMessage("GitHub tài khoản chưa có email đã xác minh");

        verifyNoInteractions(userRepository);
    }

    @Test
    @DisplayName("/user/emails loi thi coi nhu khong co email, khong nem loi HTTP ra ngoai")
    void loginWithGithub_emailsEndpointFails_isTreatedAsNoEmail() {
        // Arrange: /user khong co email, /user/emails tra 503
        http.enqueue(200, token("gh-token"))
                .enqueue(200, profile("", "octocat", ""))
                .enqueue(503, "");

        // Act & Assert: nhanh nay nuot moi exception (catch Exception -> ""), nen ket qua la
        // "chua co email da xac minh" chu khong phai loi mang
        assertThatThrownBy(() -> service.loginWithGithub("code", REDIRECT, httpRequest))
                .isInstanceOf(InvalidAccountException.class)
                .hasMessage("GitHub tài khoản chưa có email đã xác minh");
    }

    @Test
    @DisplayName("Lan dau dang nhap: tao user, thieu ten thi lay login GitHub lam fullName")
    void loginWithGithub_newUser_isCreatedWithLoginAsName() {
        // Arrange: email chua co trong DB, profile khong co name (chi co login)
        http.enqueue(200, token("gh-token")).enqueue(200, profile(EMAIL, "octocat", ""));
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());
        when(passwordEncoder.encode(anyString())).thenReturn("encoded-random");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        User result = service.loginWithGithub("code", REDIRECT, httpRequest);

        // Assert: luu user moi voi email tu GitHub, ten fallback = login, enabled=true
        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        assertThat(saved.getValue().getEmail()).isEqualTo(EMAIL);
        assertThat(saved.getValue().getFullName()).isEqualTo("octocat");
        assertThat(saved.getValue().isEnabled()).isTrue();
        assertThat(result).isSameAs(saved.getValue());
    }

    @Test
    @DisplayName("Tai khoan bi khoa hoac da xoa mem khong dang nhap duoc bang GitHub")
    void loginWithGithub_lockedOrDeletedUser_isRejected() {
        // Arrange: user da ton tai nhung bi admin khoa
        http.enqueue(200, token("gh-token")).enqueue(200, profile(EMAIL, "octocat", "Octo"));
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(
                User.builder().id(4L).email(EMAIL).fullName("Octo").enabled(true).locked(true).build()));

        // Act & Assert: khoa cua admin thang moi duong dang nhap
        assertThatThrownBy(() -> service.loginWithGithub("code", REDIRECT, httpRequest))
                .isInstanceOf(InvalidAccountException.class)
                .hasMessage("Tài khoản của bạn đã bị khóa bởi quản trị viên. Vui lòng liên hệ hỗ trợ.");

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("Tai khoan da xoa mem bi tu choi voi message rieng")
    void loginWithGithub_deletedUser_isRejected() {
        // Arrange: user da xoa mem (deletedAt != null)
        http.enqueue(200, token("gh-token")).enqueue(200, profile(EMAIL, "octocat", "Octo"));
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(
                User.builder().id(4L).email(EMAIL).fullName("Octo").enabled(true)
                        .deletedAt(LocalDateTime.now()).build()));

        // Act & Assert: message khac han message "bi khoa", de FE hien dung huong dan
        assertThatThrownBy(() -> service.loginWithGithub("code", REDIRECT, httpRequest))
                .isInstanceOf(InvalidAccountException.class)
                .hasMessage("Tài khoản không tồn tại hoặc đã bị xóa");
    }

    private static String token(String accessToken) {
        return "{\"access_token\":\"" + accessToken + "\",\"token_type\":\"bearer\"}";
    }

    private static String profile(String email, String login, String name) {
        return "{\"login\":\"" + login + "\",\"name\":\"" + name + "\",\"email\":\"" + email + "\"}";
    }
}
