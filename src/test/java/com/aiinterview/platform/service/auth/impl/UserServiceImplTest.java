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
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));

        assertThat(service.loadUserByUsername(EMAIL)).isSameAs(user);
    }

    @Test
    @DisplayName("loadUserByUsername voi email la thi bao InvalidAccountException")
    void loadUserByUsername_unknownEmail_throws() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.loadUserByUsername(EMAIL))
                .isInstanceOf(InvalidAccountException.class)
                .hasMessage("User not found with email: " + EMAIL);
    }

    @Test
    @DisplayName("findByEmail voi email la thi bao InvalidAccountException")
    void findByEmail_unknownEmail_throws() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findByEmail(EMAIL))
                .isInstanceOf(InvalidAccountException.class)
                .hasMessage("User not found with email: " + EMAIL);
    }

    @Test
    @DisplayName("findById giu nguyen message co khoang trang dau (hop dong voi FE)")
    void findById_unknownId_throws() {
        when(userRepository.findById(9L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findById(9L))
                .isInstanceOf(InvalidAccountException.class)
                .hasMessage(" User not found with id : 9");
    }

    @Test
    @DisplayName("Doi mat khau: mat khau hien tai sai thi dung lai, khong ghi gi")
    void changePassword_wrongCurrentPassword_throws() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("Wrong!123", OLD_ENCODED)).thenReturn(false);

        assertThatThrownBy(() -> service.changePassword(EMAIL, request("Wrong!123", "Str0ng!Pass")))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Mật khẩu hiện tại không đúng");

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
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("Str0ng!Old", OLD_ENCODED)).thenReturn(true);

        assertThatThrownBy(() -> service.changePassword(EMAIL, request("Str0ng!Old", newPassword)))
                .isInstanceOf(BadRequestException.class)
                .hasMessage(expectedPasswordMessage(newPassword));

        verify(userRepository, never()).save(user);
        verifyNoInteractions(userSessionService);
    }

    @Test
    @DisplayName("Doi mat khau thanh cong: ma hoa lai, tang token_version, thu hoi moi phien")
    void changePassword_success_bumpsVersionAndRevokesSessions() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("Str0ng!Old", OLD_ENCODED)).thenReturn(true);
        when(passwordEncoder.encode("Str0ng!New")).thenReturn(NEW_ENCODED);

        service.changePassword(EMAIL, request("Str0ng!Old", "Str0ng!New"));

        assertThat(user.getPassword()).isEqualTo(NEW_ENCODED);
        assertThat(user.getTokenVersion()).isEqualTo(3);
        verify(userRepository).save(user);
        verify(userSessionService).revokeAll(USER_ID);
    }

    @Test
    @DisplayName("Doi mat khau cho email khong ton tai thi bao InvalidAccountException")
    void changePassword_unknownUser_throws() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.changePassword(EMAIL, request("a", "Str0ng!New")))
                .isInstanceOf(InvalidAccountException.class);

        verifyNoInteractions(passwordEncoder, userSessionService);
    }

    @Test
    @DisplayName("Logout (revokeSessions) cung tang token_version va thu hoi phien")
    void revokeSessions_bumpsVersionAndRevokesSessions() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));

        service.revokeSessions(EMAIL);

        assertThat(user.getTokenVersion()).isEqualTo(3);
        verify(userRepository).save(user);
        verify(userSessionService).revokeAll(USER_ID);
    }

    @Test
    @DisplayName("revokeSessions cho email khong ton tai thi khong ghi gi")
    void revokeSessions_unknownUser_throws() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.revokeSessions(EMAIL))
                .isInstanceOf(InvalidAccountException.class);

        verify(userRepository, never()).save(user);
        verifyNoInteractions(userSessionService);
    }

    @Test
    @DisplayName("Avatar khong co dau phay (khong phai data URL) bi tu choi")
    void updateAvatar_notADataUrl_throws() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service.updateAvatar(EMAIL, "just-a-string"))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Ảnh đại diện phải là dữ liệu base64");

        verify(userRepository, never()).save(user);
    }

    @Test
    @DisplayName("Avatar base64 hong bi tu choi")
    void updateAvatar_undecodableBase64_throws() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service.updateAvatar(EMAIL, "data:image/png;base64,!!!!"))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Ảnh đại diện không đọc được");
    }

    @Test
    @DisplayName("Avatar rong (0 byte) bi tu choi")
    void updateAvatar_emptyPayload_throws() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service.updateAvatar(EMAIL, "data:image/png;base64,"))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Ảnh đại diện rỗng");
    }

    @Test
    @DisplayName("Avatar lon hon 2 MB bi tu choi")
    void updateAvatar_tooLarge_throws() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        byte[] tooBig = new byte[2 * 1024 * 1024 + 1];
        tooBig[0] = (byte) 0x89;
        tooBig[1] = 'P';
        tooBig[2] = 'N';
        tooBig[3] = 'G';

        assertThatThrownBy(() -> service.updateAvatar(EMAIL, dataUrl(tooBig)))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Ảnh đại diện tối đa 2 MB");

        verify(userRepository, never()).save(user);
    }

    @Test
    @DisplayName("Nhan MIME tu khai khong duoc tin: chu ky tep phai la PNG/JPG/WEBP")
    void updateAvatar_wrongFileSignature_throws() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        byte[] gif = "GIF89a.....".getBytes();

        assertThatThrownBy(() -> service.updateAvatar(EMAIL, dataUrl(gif)))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Chỉ nhận ảnh PNG, JPG hoặc WEBP");
    }

    @Test
    @DisplayName("PNG hop le duoc luu nguyen chuoi data URL")
    void updateAvatar_png_isSaved() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        String dataUrl = dataUrl(png());

        service.updateAvatar(EMAIL, dataUrl);

        assertThat(user.getAvatar()).isEqualTo(dataUrl);
        verify(userRepository).save(user);
    }

    @Test
    @DisplayName("JPEG va WEBP cung duoc chap nhan theo dung chu ky tep")
    void updateAvatar_jpegAndWebp_areSaved() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        String jpegUrl = dataUrl(jpeg());
        String webpUrl = dataUrl(webp());

        service.updateAvatar(EMAIL, jpegUrl);
        assertThat(user.getAvatar()).isEqualTo(jpegUrl);

        service.updateAvatar(EMAIL, webpUrl);
        assertThat(user.getAvatar()).isEqualTo(webpUrl);

        verify(userRepository, times(2)).save(user);
    }

    @Test
    @DisplayName("Xoa avatar dat cot avatar = null")
    void clearAvatar_setsNull() {
        user.setAvatar("data:image/png;base64,AAA");
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));

        service.clearAvatar(EMAIL);

        assertThat(user.getAvatar()).isNull();
        verify(userRepository).save(user);
    }

    @Test
    @DisplayName("Cap nhat avatar cho tai khoan khong ton tai thi bao InvalidAccountException")
    void updateAvatar_unknownUser_throws() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateAvatar(EMAIL, dataUrl(png())))
                .isInstanceOf(InvalidAccountException.class);

        verify(userRepository, never()).save(user);
    }

    private static ChangePasswordRequest request(String current, String next) {
        ChangePasswordRequest request = new ChangePasswordRequest();
        request.setCurrentPassword(current);
        request.setNewPassword(next);
        return request;
    }

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
