package com.aiinterview.platform.service.auth.impl;

import com.aiinterview.platform.common.exception.BadRequestException;
import com.aiinterview.platform.common.exception.InvalidAccountException;
import com.aiinterview.platform.model.dto.request.ChangePasswordRequest;
import com.aiinterview.platform.model.entity.User;
import com.aiinterview.platform.model.enums.Role;
import com.aiinterview.platform.model.repository.UserRepository;
import com.aiinterview.platform.service.auth.UserSessionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Arrays;
import java.util.Base64;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Profile writes and password change. Password change is the revocation point of the whole auth
 * model: token_version is bumped AND every user_session row is revoked, so old access and refresh
 * tokens die immediately instead of waiting for their TTL.
 *
 * <p>Avatar is stored inline as a data URL, so the write path validates the decoded bytes, not the
 * self-declared MIME label.</p>
 *
 * <p>Mỗi test chia 3 khối comment: <b>Arrange</b> → <b>Act</b> → <b>Assert</b>.</p>
 */
@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    private static final String EMAIL = "candidate@example.com";
    private static final String OLD_ENCODED = "$2a$10$old";
    private static final String NEW_ENCODED = "$2a$10$new";
    private static final long USER_ID = 7L;

    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private UserSessionService userSessionService;
    @InjectMocks
    private UserServiceImpl service;

    private User user;

    @BeforeEach
    void setUp() {
        user = User.builder()
                .id(USER_ID)
                .email(EMAIL)
                .password(OLD_ENCODED)
                .fullName("Candidate")
                .role(Role.ROLE_USER)
                .enabled(true)
                .tokenVersion(2)
                .build();
    }

    @Test
    @DisplayName("loadUserByUsername tra ve dung dong users")
    void loadUserByUsername_returnsUser() {
        // Arrange: DB co dong users tuong ung email
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));

        // Act & Assert: tra ve chinh instance (isSameAs) cho UserDetailsService cua Spring Security
        assertThat(service.loadUserByUsername(EMAIL)).isSameAs(user);
    }

    @Test
    @DisplayName("loadUserByUsername voi email la thi bao InvalidAccountException")
    void loadUserByUsername_unknownEmail_throws() {
        // Arrange: email khong con trong DB (tk bi xoa / go sai)
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());

        // Act & Assert: nem InvalidAccountException, khong tra null (tranh NPE sau do)
        assertThatThrownBy(() -> service.loadUserByUsername(EMAIL))
                .isInstanceOf(InvalidAccountException.class)
                .hasMessage("User not found with email: " + EMAIL);
    }

    @Test
    @DisplayName("findByEmail voi email la thi bao InvalidAccountException")
    void findByEmail_unknownEmail_throws() {
        // Arrange: email khong ton tai
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());

        // Act & Assert: cung hop dong loi voi loadUserByUsername
        assertThatThrownBy(() -> service.findByEmail(EMAIL))
                .isInstanceOf(InvalidAccountException.class)
                .hasMessage("User not found with email: " + EMAIL);
    }

    @Test
    @DisplayName("findById giu nguyen message co khoang trang dau (hop dong voi FE)")
    void findById_unknownId_throws() {
        // Arrange: id khong ton tai
        when(userRepository.findById(9L)).thenReturn(Optional.empty());

        // Act & Assert: message giu nguyen khoang trang dau de khong pha vo test/hop dong FE
        assertThatThrownBy(() -> service.findById(9L))
                .isInstanceOf(InvalidAccountException.class)
                .hasMessage(" User not found with id : 9");
    }

    @Test
    @DisplayName("Doi mat khau: mat khau hien tai sai thi dung lai, khong ghi gi")
    void changePassword_wrongCurrentPassword_throws() {
        // Arrange: passwordEncoder xac nhan mat khau hien tai KHONG khop
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("Wrong!123", OLD_ENCODED)).thenReturn(false);

        // Act & Assert: dung ngay o buoc kiem tra, khong di tiep
        assertThatThrownBy(() -> service.changePassword(EMAIL, request("Wrong!123", "Str0ng!Pass")))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Mật khẩu hiện tại không đúng");

        // Assert: khong luu user va khong dung toi phien (tranh logout oan)
        verify(userRepository, never()).save(user);
        verifyNoInteractions(userSessionService);
    }

    @ParameterizedTest(name = "{0} => {1}")
    @CsvSource({
            "Ab1!, Mat khau moi phai co toi thieu 8 ky tu",
            "abcdefgh1!, phai co chu hoa",
            "ABCDEFGH1!, phai co chu thuong",
            "Abcdefgh!, phai co chu so",
            "Abcdefg1, phai co ky tu dac biet"
    })
    @DisplayName("Bon quy tac mat khau moi tach rieng de message chi dung mot loi")
    void changePassword_newPasswordRuleViolations(String newPassword, String ignoredHint) {
        // Arrange: mat khau hien tai dung, chi mat khau moi vi pham 1 quy tac
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("Str0ng!Old", OLD_ENCODED)).thenReturn(true);

        // Act & Assert: message chi neu dung 1 loi (khong gop), suy ra tu chinh mat khau trong helper
        // (cot thu 2 cua CsvSource chi de doc ten ca, khong dung khi assert)
        assertThatThrownBy(() -> service.changePassword(EMAIL, request("Str0ng!Old", newPassword)))
                .isInstanceOf(BadRequestException.class)
                .hasMessage(expectedPasswordMessage(newPassword));

        // Assert: mat khau moi sai thi khong ghi DB va khong thu hoi phien
        verify(userRepository, never()).save(user);
        verifyNoInteractions(userSessionService);
    }

    @Test
    @DisplayName("Doi mat khau thanh cong: ma hoa lai, tang token_version, thu hoi moi phien")
    void changePassword_success_bumpsVersionAndRevokesSessions() {
        // Arrange: mat khau hien tai dung, mat khau moi hop le
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("Str0ng!Old", OLD_ENCODED)).thenReturn(true);
        when(passwordEncoder.encode("Str0ng!New")).thenReturn(NEW_ENCODED);

        // Act
        service.changePassword(EMAIL, request("Str0ng!Old", "Str0ng!New"));

        // Assert: luu hash moi, KHONG luu mat khau ro
        assertThat(user.getPassword()).isEqualTo(NEW_ENCODED);

        // Assert: tv 2 -> 3 nen moi access/refresh token cu bi JwtFilter/JwtUtils dao thai ngay
        assertThat(user.getTokenVersion()).isEqualTo(3);
        verify(userRepository).save(user);

        // Assert: thu hoi toan bo phien (bang user_session) de khong con refresh token song
        verify(userSessionService).revokeAll(USER_ID);
    }

    @Test
    @DisplayName("Doi mat khau cho email khong ton tai thi bao InvalidAccountException")
    void changePassword_unknownUser_throws() {
        // Arrange: email khong co trong DB
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());

        // Act & Assert: bao khong tim thay tai khoan
        assertThatThrownBy(() -> service.changePassword(EMAIL, request("a", "Str0ng!New")))
                .isInstanceOf(InvalidAccountException.class);

        // Assert: khong ton tai user thi khong duoc encode / revoke gi
        verifyNoInteractions(passwordEncoder, userSessionService);
    }

    @Test
    @DisplayName("Logout (revokeSessions) cung tang token_version va thu hoi phien")
    void revokeSessions_bumpsVersionAndRevokesSessions() {
        // Arrange: user dang co phien voi tv=2
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));

        // Act: logout / logout-all deu di qua day
        service.revokeSessions(EMAIL);

        // Assert: tang tv (dao thai access token) + thu hoi phien (dao thai refresh token)
        assertThat(user.getTokenVersion()).isEqualTo(3);
        verify(userRepository).save(user);
        verify(userSessionService).revokeAll(USER_ID);
    }

    @Test
    @DisplayName("revokeSessions cho email khong ton tai thi khong ghi gi")
    void revokeSessions_unknownUser_throws() {
        // Arrange: email khong co trong DB (token cua tk da bi xoa)
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());

        // Act & Assert: nem loi thay vi im lang
        assertThatThrownBy(() -> service.revokeSessions(EMAIL))
                .isInstanceOf(InvalidAccountException.class);

        // Assert: khong ghi DB, khong goi tang phien
        verify(userRepository, never()).save(user);
        verifyNoInteractions(userSessionService);
    }

    @Test
    @DisplayName("Avatar khong co dau phay (khong phai data URL) bi tu choi")
    void updateAvatar_notADataUrl_throws() {
        // Arrange: chuoi thuong, khong phai dinh dang "data:...,base64,..."
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));

        // Act & Assert: tu choi som truoc khi decode
        assertThatThrownBy(() -> service.updateAvatar(EMAIL, "just-a-string"))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Ảnh đại diện phải là dữ liệu base64");

        // Assert: khong luu gi khi input sai dinh dang
        verify(userRepository, never()).save(user);
    }

    @Test
    @DisplayName("Avatar base64 hong bi tu choi")
    void updateAvatar_undecodableBase64_throws() {
        // Arrange: dung tien to data URL nhung payload "!!!!" khong phai base64 hop le
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));

        // Act & Assert: bat loi decode va doi thanh BadRequest (khong lo 500)
        assertThatThrownBy(() -> service.updateAvatar(EMAIL, "data:image/png;base64,!!!!"))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Ảnh đại diện không đọc được");
    }

    @Test
    @DisplayName("Avatar rong (0 byte) bi tu choi")
    void updateAvatar_emptyPayload_throws() {
        // Arrange: header hop le nhung payload rong
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));

        // Act & Assert: 0 byte khong phai anh => tu choi
        assertThatThrownBy(() -> service.updateAvatar(EMAIL, "data:image/png;base64,"))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Ảnh đại diện rỗng");
    }

    @Test
    @DisplayName("Avatar lon hon 2 MB bi tu choi")
    void updateAvatar_tooLarge_throws() {
        // Arrange: payload 2 MB + 1 byte, co chu ky PNG hop le (de loi roi vao nhanh kich thuoc)
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        byte[] tooBig = new byte[2 * 1024 * 1024 + 1];
        tooBig[0] = (byte) 0x89;
        tooBig[1] = 'P';
        tooBig[2] = 'N';
        tooBig[3] = 'G';

        // Act & Assert: kiem tra size truoc khi luu (avatar luu inline trong cot nen phai chan)
        assertThatThrownBy(() -> service.updateAvatar(EMAIL, dataUrl(tooBig)))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Ảnh đại diện tối đa 2 MB");

        // Assert: qua gioi han thi khong ghi DB
        verify(userRepository, never()).save(user);
    }

    @Test
    @DisplayName("Nhan MIME tu khai khong duoc tin: chu ky tep phai la PNG/JPG/WEBP")
    void updateAvatar_wrongFileSignature_throws() {
        // Arrange: bytes la GIF (GIF89a) nhung header data URL van khai "image/png"
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        byte[] gif = "GIF89a.....".getBytes();

        // Act & Assert: server doc magic bytes, khong tin nhan MIME do client gui
        assertThatThrownBy(() -> service.updateAvatar(EMAIL, dataUrl(gif)))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Chỉ nhận ảnh PNG, JPG hoặc WEBP");
    }

    @Test
    @DisplayName("PNG hop le duoc luu nguyen chuoi data URL")
    void updateAvatar_png_isSaved() {
        // Arrange: chu ky PNG dung (89 50 4E 47)
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        String dataUrl = dataUrl(png());

        // Act
        service.updateAvatar(EMAIL, dataUrl);

        // Assert: luu nguyen chuoi data URL (FE doc truc tiep lam src), khong cat bot gi
        assertThat(user.getAvatar()).isEqualTo(dataUrl);
        verify(userRepository).save(user);
    }

    @Test
    @DisplayName("JPEG va WEBP cung duoc chap nhan theo dung chu ky tep")
    void updateAvatar_jpegAndWebp_areSaved() {
        // Arrange: 1 anh JPEG (FF D8 FF) va 1 anh WEBP (RIFF....WEBP)
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        String jpegUrl = dataUrl(jpeg());
        String webpUrl = dataUrl(webp());

        // Act & Assert: JPEG duoc nhan
        service.updateAvatar(EMAIL, jpegUrl);
        assertThat(user.getAvatar()).isEqualTo(jpegUrl);

        // Act & Assert: WEBP duoc nhan (ky hieu o byte 8..11 chu khong phai byte 0)
        service.updateAvatar(EMAIL, webpUrl);
        assertThat(user.getAvatar()).isEqualTo(webpUrl);

        // Assert: 2 lan cập nhật => 2 lan luu
        verify(userRepository, times(2)).save(user);
    }

    @Test
    @DisplayName("Xoa avatar dat cot avatar = null")
    void clearAvatar_setsNull() {
        // Arrange: user dang co avatar
        user.setAvatar("data:image/png;base64,AAA");
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));

        // Act
        service.clearAvatar(EMAIL);

        // Assert: dat null (khong luu chuoi rong) va van luu lai
        assertThat(user.getAvatar()).isNull();
        verify(userRepository).save(user);
    }

    @Test
    @DisplayName("Cap nhat avatar cho tai khoan khong ton tai thi bao InvalidAccountException")
    void updateAvatar_unknownUser_throws() {
        // Arrange: email khong co trong DB
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());

        // Act & Assert: bao khong tim thay tai khoan (khac voi loi du lieu anh)
        assertThatThrownBy(() -> service.updateAvatar(EMAIL, dataUrl(png())))
                .isInstanceOf(InvalidAccountException.class);

        // Assert: khong luu gi
        verify(userRepository, never()).save(user);
    }

    private static ChangePasswordRequest request(String current, String next) {
        ChangePasswordRequest request = new ChangePasswordRequest();
        request.setCurrentPassword(current);
        request.setNewPassword(next);
        return request;
    }

    /** Suy ra message loi tu chinh mat khau, theo dung thu tu kiem tra cua service. */
    private static String expectedPasswordMessage(String password) {
        if (password.length() < 8) {
            return "Mật khẩu mới phải có tối thiểu 8 ký tự";
        }
        boolean hasUpper = password.chars().anyMatch(Character::isUpperCase);
        boolean hasLower = password.chars().anyMatch(Character::isLowerCase);
        if (!hasUpper || !hasLower) {
            return "Mật khẩu mới phải chứa ít nhất một chữ hoa và một chữ thường";
        }
        if (password.chars().noneMatch(Character::isDigit)) {
            return "Mật khẩu mới phải chứa ít nhất một chữ số";
        }
        return "Mật khẩu mới phải chứa ít nhất một ký tự đặc biệt (@#$%^&+=!)";
    }

    private static String dataUrl(byte[] bytes) {
        return "data:image/png;base64," + Base64.getEncoder().encodeToString(bytes);
    }

    private static byte[] png() {
        byte[] bytes = new byte[11];
        bytes[0] = (byte) 0x89;
        bytes[1] = 'P';
        bytes[2] = 'N';
        bytes[3] = 'G';
        return bytes;
    }

    private static byte[] jpeg() {
        byte[] bytes = new byte[8];
        Arrays.fill(bytes, (byte) 0x11);
        bytes[0] = (byte) 0xFF;
        bytes[1] = (byte) 0xD8;
        bytes[2] = (byte) 0xFF;
        return bytes;
    }

    private static byte[] webp() {
        byte[] bytes = new byte[16];
        bytes[0] = 'R';
        bytes[1] = 'I';
        bytes[2] = 'F';
        bytes[3] = 'F';
        bytes[8] = 'W';
        bytes[9] = 'E';
        bytes[10] = 'B';
        bytes[11] = 'P';
        return bytes;
    }
}
