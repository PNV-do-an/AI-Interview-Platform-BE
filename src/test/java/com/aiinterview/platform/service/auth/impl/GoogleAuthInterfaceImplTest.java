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
import java.time.Instant;
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
 * POST /auth/google (folder 00). The whole flow is: call Google tokeninfo, then trust the returned
 * claims only after aud / iss / exp / email_verified pass. Since R1 the HttpClient comes from
 * HttpClientConfig, so the fake below stands in for Google and every branch is reachable.
 *
 * <p>The ID token itself is never parsed locally — Google tokeninfo is the source of truth, which is
 * why an "expired" token is just a claim with a past `exp`.</p>
 *
 * <p>Mỗi test chia 3 khối comment: <b>Arrange</b> (dựng gì) → <b>Act</b> (gọi gì) → <b>Assert</b>
 * (khẳng định gì). Test nào chỉ có một lời gọi thì gộp Act &amp; Assert cho khỏi vụn.</p>
 */
@ExtendWith(MockitoExtension.class)
class GoogleAuthInterfaceImplTest {

    private static final String CLIENT_ID = "google-client-id.apps.googleusercontent.com";
    private static final String EMAIL = "candidate@gmail.com";
    private static final String ISSUER = "https://accounts.google.com";

    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordEncoder passwordEncoder;

    private StubHttpClient http;
    private GoogleAuthInterfaceImpl service;
    private HttpServletRequest httpRequest;

    @BeforeEach
    void setUp() {
        http = new StubHttpClient();
        service = new GoogleAuthInterfaceImpl(userRepository, passwordEncoder, http);
        ReflectionTestUtils.setField(service, "clientId", CLIENT_ID);
        httpRequest = mock(HttpServletRequest.class);
    }

    @Test
    @DisplayName("Chua cau hinh GOOGLE_CLIENT_ID thi getClientId tra chuoi rong de FE an nut")
    void getClientId_whenNotConfigured_returnsEmptyString() {
        // Arrange: gia lap ung dung chua bat Google login
        ReflectionTestUtils.setField(service, "clientId", null);

        // Act & Assert: FE dua vao chuoi rong nay de an nut "Dang nhap bang Google"
        assertThat(service.getClientId()).isEmpty();
    }

    @Test
    @DisplayName("Da cau hinh thi getClientId tra dung gia tri cho FE dung OAuth")
    void getClientId_whenConfigured_returnsValue() {
        // Arrange: client id da duoc set trong @BeforeEach

        // Act & Assert: tra nguyen van gia tri cau hinh, khong trim khong bien doi
        assertThat(service.getClientId()).isEqualTo(CLIENT_ID);
    }

    @Test
    @DisplayName("Chua cau hinh client id thi tu choi ngay, khong goi Google va khong cham database")
    void loginWithGoogle_whenNotConfigured_isRejected() {
        // Arrange: cau hinh rong = chua bat tinh nang
        ReflectionTestUtils.setField(service, "clientId", "");

        // Act & Assert: nem loi cau hinh, khong phai loi "token sai"
        assertThatThrownBy(() -> service.loginWithGoogle("id-token", httpRequest))
                .isInstanceOf(InvalidAccountException.class)
                .hasMessage("Đăng nhập Google chưa được cấu hình trên hệ thống");

        // Khong duoc goi Google va khong duoc cham DB khi tinh nang chua bat
        assertThat(http.requestUris()).isEmpty();
        verifyNoInteractions(userRepository, passwordEncoder);
    }

    @Test
    @DisplayName("Thieu id token thi tu choi truoc khi goi Google")
    void loginWithGoogle_withoutToken_isRejected() {
        // Arrange: FE gui chuoi toan khoang trang

        // Act & Assert: chan o cua, message rieng de FE biet loi phia client
        assertThatThrownBy(() -> service.loginWithGoogle("  ", httpRequest))
                .isInstanceOf(InvalidAccountException.class)
                .hasMessage("Thiếu token Google");

        // Khong ton cong goi mang khi input da vo nghia
        assertThat(http.requestUris()).isEmpty();
        verifyNoInteractions(userRepository);
    }

    @Test
    @DisplayName("Google tokeninfo tra status khac 200 thi token bi coi la khong hop le")
    void loginWithGoogle_tokenInfoRejectsToken() {
        // Arrange: Google tra 400 cho token rac
        http.enqueue(400, "{\"error\":\"invalid_token\"}");

        // Act & Assert: coi nhu token khong hop le, khong lo body loi cua Google ra ngoai
        assertThatThrownBy(() -> service.loginWithGoogle("bad-token", httpRequest))
                .isInstanceOf(InvalidAccountException.class)
                .hasMessage("Google token không hợp lệ");

        // Token sai thi khong duoc di tiep den buoc tim/tao user
        verifyNoInteractions(userRepository);
    }

    @Test
    @DisplayName("Khong ket noi duoc Google thi bao loi mang, khong lo stack trace ra ngoai")
    void loginWithGoogle_networkFailure_isWrapped() {
        // Arrange: gia lap loi mang ngay o loi goi dau tien
        http.failNextWith(new IOException("connection reset"));

        // Act & Assert: message kem nguyen nhan goc de con debug
        assertThatThrownBy(() -> service.loginWithGoogle("id-token", httpRequest))
                .isInstanceOf(InvalidAccountException.class)
                .hasMessage("Không thể xác minh token Google: connection reset");

        verifyNoInteractions(userRepository);
    }

    @Test
    @DisplayName("aud khac client id = token cua ung dung khac")
    void loginWithGoogle_wrongAudience_isRejected() {
        // Arrange: token that nhung phat cho mot client id khac (tan cong token confusion)
        http.enqueue(200, claims("another-client-id", ISSUER, future(), EMAIL, true, "Candidate"));

        // Act & Assert: tu choi truoc khi dung email trong token
        assertThatThrownBy(() -> service.loginWithGoogle("id-token", httpRequest))
                .isInstanceOf(InvalidAccountException.class)
                .hasMessage("Token Google không thuộc ứng dụng này");

        verifyNoInteractions(userRepository);
    }

    @Test
    @DisplayName("iss khong phai Google thi tu choi")
    void loginWithGoogle_wrongIssuer_isRejected() {
        // Arrange: token do mot IdP khac phat hanh
        http.enqueue(200, claims(CLIENT_ID, "https://evil.example.com", future(), EMAIL, true, "X"));

        // Act & Assert: thu tu kiem tra la aud -> iss, nen case nay roi vao nhanh iss
        assertThatThrownBy(() -> service.loginWithGoogle("id-token", httpRequest))
                .isInstanceOf(InvalidAccountException.class)
                .hasMessage("Nguồn phát hành token không hợp lệ");
    }

    @Test
    @DisplayName("Token het han theo claim exp bi tu choi")
    void loginWithGoogle_expiredToken_isRejected() {
        // Arrange: mint claims voi exp o qua khu (khong phai token that, chi la JSON)
        long past = Instant.now().getEpochSecond() - 60;
        http.enqueue(200, claims(CLIENT_ID, ISSUER, past, EMAIL, true, "Candidate"));

        // Act & Assert: het han la loi rieng, khong gop chung voi "token khong hop le"
        assertThatThrownBy(() -> service.loginWithGoogle("id-token", httpRequest))
                .isInstanceOf(InvalidAccountException.class)
                .hasMessage("Token Google đã hết hạn");
    }

    @Test
    @DisplayName("email_verified=false thi tu choi du email co ton tai")
    void loginWithGoogle_unverifiedEmail_isRejected() {
        // Arrange: claims day du nhung email chua duoc Google xac minh
        http.enqueue(200, claims(CLIENT_ID, ISSUER, future(), EMAIL, false, "Candidate"));

        // Act & Assert: khong cho chiem tai khoan bang email chua xac minh
        assertThatThrownBy(() -> service.loginWithGoogle("id-token", httpRequest))
                .isInstanceOf(InvalidAccountException.class)
                .hasMessage("Email Google chưa được xác minh");

        verifyNoInteractions(userRepository);
    }

    @Test
    @DisplayName("Claim email rong cung bi coi la chua xac minh")
    void loginWithGoogle_blankEmail_isRejected() {
        // Arrange: verified=true nhung email la chuoi rong (du lieu bat thuong tu Google)
        http.enqueue(200, claims(CLIENT_ID, ISSUER, future(), "", true, "Candidate"));

        // Act & Assert: rong cung di chung nhanh "chua xac minh" thay vi tao user email rong
        assertThatThrownBy(() -> service.loginWithGoogle("id-token", httpRequest))
                .isInstanceOf(InvalidAccountException.class)
                .hasMessage("Email Google chưa được xác minh");
    }

    @Test
    @DisplayName("Lan dau dang nhap bang Google: tao user da kich hoat voi mat khau ngau nhien")
    void loginWithGoogle_newUser_isCreated() {
        // Arrange: claims hop le nhung email chua co trong DB
        http.enqueue(200, claims(CLIENT_ID, ISSUER, future(), EMAIL, true, "Nguyen Van A"));
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());
        when(passwordEncoder.encode(anyString())).thenReturn("encoded-random");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act: dang nhap lan dau
        User result = service.loginWithGoogle("id-token", httpRequest);

        // Assert: luu dung email/ten tu Google, mat khau random (khong the dang nhap bang mat khau),
        // va phai enabled=true vi Google da xac minh email ho
        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        assertThat(saved.getValue().getEmail()).isEqualTo(EMAIL);
        assertThat(saved.getValue().getFullName()).isEqualTo("Nguyen Van A");
        assertThat(saved.getValue().getPassword()).isEqualTo("encoded-random");
        assertThat(saved.getValue().isEnabled()).isTrue();
        assertThat(result).isSameAs(saved.getValue());
    }

    @Test
    @DisplayName("Claim name rong thi lay phan truoc @ lam fullName")
    void loginWithGoogle_newUserWithoutName_fallsBackToEmailPrefix() {
        // Arrange: user moi, claim name rong
        http.enqueue(200, claims(CLIENT_ID, ISSUER, future(), EMAIL, true, ""));
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());
        when(passwordEncoder.encode(anyString())).thenReturn("encoded-random");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        service.loginWithGoogle("id-token", httpRequest);

        // Assert: khong de fullName rong (UI se hien khoang trang), lay phan truoc @
        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        assertThat(saved.getValue().getFullName()).isEqualTo("candidate");
    }

    @Test
    @DisplayName("User da ton tai thi dung lai, khong tao ban ghi moi")
    void loginWithGoogle_existingUser_isReused() {
        // Arrange: email da co user (ke ca ten khac ten tren Google)
        User existing = User.builder().id(3L).email(EMAIL).fullName("Old Name")
                .enabled(true).tokenVersion(1).build();
        http.enqueue(200, claims(CLIENT_ID, ISSUER, future(), EMAIL, true, "New Name"));
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(existing));

        // Act
        User result = service.loginWithGoogle("id-token", httpRequest);

        // Assert: tra dung user cu, khong ghi de ten cua ho, khong save
        assertThat(result).isSameAs(existing);
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("Tai khoan da xoa mem khong duoc dang nhap lai bang Google")
    void loginWithGoogle_deletedUser_isRejected() {
        // Arrange: email ton tai nhung da xoa mem (deletedAt != null)
        User deleted = User.builder().id(3L).email(EMAIL).fullName("X").enabled(true)
                .deletedAt(LocalDateTime.now()).build();
        http.enqueue(200, claims(CLIENT_ID, ISSUER, future(), EMAIL, true, "X"));
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(deleted));

        // Act & Assert: xoa mem phai la cua mot chieu, OAuth khong hoi sinh tai khoan
        assertThatThrownBy(() -> service.loginWithGoogle("id-token", httpRequest))
                .isInstanceOf(InvalidAccountException.class)
                .hasMessage("Tài khoản không tồn tại hoặc đã bị xóa");
    }

    @Test
    @DisplayName("Tai khoan bi admin khoa thi Google cung khong mo cua duoc")
    void loginWithGoogle_lockedUser_isRejected() {
        // Arrange: user con song nhung bi admin khoa (locked=true)
        User locked = User.builder().id(3L).email(EMAIL).fullName("X").enabled(true).locked(true).build();
        http.enqueue(200, claims(CLIENT_ID, ISSUER, future(), EMAIL, true, "X"));
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(locked));

        // Act & Assert: khoa cua admin thang moi duong dang nhap, ke ca OAuth
        assertThatThrownBy(() -> service.loginWithGoogle("id-token", httpRequest))
                .isInstanceOf(InvalidAccountException.class)
                .hasMessage("Tài khoản của bạn đã bị khóa bởi quản trị viên. Vui lòng liên hệ hỗ trợ.");
    }

    @Test
    @DisplayName("Tokeninfo duoc goi dung mot lan, kem id_token trong query")
    void loginWithGoogle_callsTokenInfoOnce() {
        // Arrange: mot user binh thuong de di het luong thanh cong
        http.enqueue(200, claims(CLIENT_ID, ISSUER, future(), EMAIL, true, "Candidate"));
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(
                User.builder().id(1L).email(EMAIL).fullName("Candidate").enabled(true).build()));

        // Act
        service.loginWithGoogle("the-id-token", httpRequest);

        // Assert: dung 1 loi goi (khong goi lai tokeninfo de lay them claim) va id_token
        // phai nam trong query string, khong phai header
        assertThat(http.requestUris()).hasSize(1);
        assertThat(http.requestUris().get(0))
                .startsWith("https://oauth2.googleapis.com/tokeninfo?id_token=")
                .endsWith("the-id-token");
    }

    private static long future() {
        return Instant.now().getEpochSecond() + 3600;
    }

    private static String claims(String aud, String issuer, long exp, String email,
                                 boolean verified, String name) {
        return "{\"aud\":\"" + aud + "\",\"iss\":\"" + issuer + "\",\"exp\":" + exp
                + ",\"email\":\"" + email + "\",\"email_verified\":" + verified
                + ",\"name\":\"" + name + "\"}";
    }
}
