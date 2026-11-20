package com.aiinterview.platform.service.auth.impl;

import com.aiinterview.platform.common.exception.BadRequestException;
import com.aiinterview.platform.common.exception.InvalidAccountException;
import com.aiinterview.platform.model.dto.request.ChangePasswordRequest;
import com.aiinterview.platform.model.entity.User;
import com.aiinterview.platform.model.repository.UserRepository;
import com.aiinterview.platform.service.auth.UserSessionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Base64;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * UserService: đổi mật khẩu (spec 008 US-04) và ảnh đại diện (spec 011).
 * <p>
 * Hai chỗ dễ sai đều được kiểm tra ở đây: mật khẩu mới phải qua 4 quy tắc riêng biệt (mỗi lỗi
 * nêu đúng lý do), và ảnh phải được nhận diện bằng chữ ký tệp chứ không tin nhãn MIME trong data URL.
 */
class UserServiceImplTest {

    private static final String EMAIL = "candidate@aiinterview.vn";

    private UserRepository userRepository;
    private PasswordEncoder passwordEncoder;
    private UserSessionService userSessionService;
    private UserServiceImpl service;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);
        userSessionService = mock(UserSessionService.class);
        service = new UserServiceImpl(userRepository, passwordEncoder, userSessionService);
    }

    // ── đọc user ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("loadUserByUsername trả chính entity User (User implements UserDetails)")
    void loadUserByUsername_returnsUser() {
        User user = user(1L);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));

        assertThat(service.loadUserByUsername(EMAIL)).isSameAs(user);
    }

    @Test
    @DisplayName("Email không tồn tại: Security ném InvalidAccountException, không phải NPE")
    void loadUserByUsername_missingUserThrows() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.loadUserByUsername(EMAIL))
                .isInstanceOf(InvalidAccountException.class)
                .hasMessageContaining(EMAIL);
    }

    @Test
    @DisplayName("findById không thấy: message nêu id để log dễ tra")
    void findById_missingUserThrows() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findById(99L))
                .isInstanceOf(InvalidAccountException.class)
                .hasMessageContaining("99");
    }

    // ── đổi mật khẩu ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("Sai mật khẩu hiện tại: 400 và không đụng vào DB hay session")
    void changePassword_wrongCurrentPasswordRejectedBeforeAnythingElse() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user(1L)));
        when(passwordEncoder.matches("sai", "hash")).thenReturn(false);

        assertThatThrownBy(() -> service.changePassword(EMAIL, request("sai", "Abcdef1!")))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Mật khẩu hiện tại không đúng");

        verify(userRepository, never()).save(any());
        verifyNoInteractions(userSessionService);
    }

    @Test
    @DisplayName("Mật khẩu mới < 8 ký tự bị chặn với đúng thông điệp")
    void changePassword_newPasswordTooShort() {
        assertRejected("Abcde1!", "Mật khẩu mới phải có tối thiểu 8 ký tự");
    }

    @Test
    @DisplayName("Thiếu chữ hoa hoặc chữ thường bị chặn")
    void changePassword_requiresUpperAndLowerCase() {
        assertRejected("abcdefg1!", "Mật khẩu mới phải chứa ít nhất một chữ hoa và một chữ thường");
        assertRejected("ABCDEFG1!", "Mật khẩu mới phải chứa ít nhất một chữ hoa và một chữ thường");
    }

    @Test
    @DisplayName("Thiếu chữ số bị chặn")
    void changePassword_requiresDigit() {
        assertRejected("Abcdefgh!", "Mật khẩu mới phải chứa ít nhất một chữ số");
    }

    @Test
    @DisplayName("Thiếu ký tự đặc biệt bị chặn")
    void changePassword_requiresSpecialCharacter() {
        assertRejected("Abcdefg1", "Mật khẩu mới phải chứa ít nhất một ký tự đặc biệt (@#$%^&+=!)");
    }

    @Test
    @DisplayName("Mật khẩu hợp lệ: encode, tăng tokenVersion, save, revokeAll(session)")
    void changePassword_validPasswordRotatesCredentialsAndKillsSessions() {
        User user = user(1L);
        user.setTokenVersion(4);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("cu", "hash")).thenReturn(true);
        when(passwordEncoder.encode("Abcdef1!")).thenReturn("new-hash");

        service.changePassword(EMAIL, request("cu", "Abcdef1!"));

        assertThat(user.getPassword()).isEqualTo("new-hash");
        // Bắt buộc +1: access/refresh token cũ mang claim "tv" cũ nên bị JwtFilter từ chối.
        assertThat(user.getTokenVersion()).isEqualTo(5);
        verify(userRepository).save(user);
        verify(userSessionService).revokeAll(1L);
    }

    // ── thu hồi phiên ────────────────────────────────────────────────────────

    @Test
    @DisplayName("logout/revokeSessions: tăng tokenVersion rồi revokeAll để /refresh và /me cùng thấy")
    void revokeSessions_bumpsTokenVersionAndRevokes() {
        User user = user(7L);
        user.setTokenVersion(2);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));

        service.revokeSessions(EMAIL);

        assertThat(user.getTokenVersion()).isEqualTo(3);
        verify(userRepository).save(user);
        verify(userSessionService).revokeAll(7L);
    }

    @Test
    @DisplayName("revokeSessions cho email không tồn tại: 401, không revoke nhầm id")
    void revokeSessions_unknownEmailThrows() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.revokeSessions(EMAIL))
                .isInstanceOf(InvalidAccountException.class);
        verify(userSessionService, never()).revokeAll(anyLong());
    }

    // ── ảnh đại diện ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("Avatar PNG hợp lệ được lưu nguyên data URL")
    void updateAvatar_acceptsPng() {
        User user = user(1L);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        String dataUrl = dataUrl("image/png", pngBytes());

        service.updateAvatar(EMAIL, dataUrl);

        assertThat(user.getAvatar()).isEqualTo(dataUrl);
        verify(userRepository).save(user);
    }

    @Test
    @DisplayName("JPEG và WEBP cũng được nhận (chữ ký tệp, không phải nhãn MIME)")
    void updateAvatar_acceptsJpegAndWebp() {
        User user = user(1L);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));

        service.updateAvatar(EMAIL, dataUrl("image/jpeg", jpegBytes()));
        assertThat(user.getAvatar()).startsWith("data:image/jpeg");

        service.updateAvatar(EMAIL, dataUrl("image/webp", webpBytes()));
        assertThat(user.getAvatar()).startsWith("data:image/webp");
    }

    @Test
    @DisplayName("Nhãn MIME khai man không qua mặt được: nhãn png nhưng bytes là GIF thì bị chặn")
    void updateAvatar_mimeLabelIsNotTrusted() {
        User user = user(1L);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service.updateAvatar(EMAIL,
                dataUrl("image/png", "GIF89a-not-an-image".getBytes())))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Chỉ nhận ảnh PNG, JPG hoặc WEBP");

        assertThat(user.getAvatar()).isNull();
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("Không có dấu phẩy phân cách: báo 'phải là dữ liệu base64'")
    void updateAvatar_missingCommaRejected() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user(1L)));

        assertThatThrownBy(() -> service.updateAvatar(EMAIL, "image/png;base64 AAAA"))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Ảnh đại diện phải là dữ liệu base64");
    }

    @Test
    @DisplayName("Base64 hỏng: báo 'không đọc được' chứ không lộ stacktrace")
    void updateAvatar_invalidBase64Rejected() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user(1L)));

        assertThatThrownBy(() -> service.updateAvatar(EMAIL, "data:image/png;base64,@@@not-base64@@@"))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Ảnh đại diện không đọc được");
    }

    @Test
    @DisplayName("Ảnh rỗng (data URL chỉ có phần đầu) bị chặn")
    void updateAvatar_emptyPayloadRejected() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user(1L)));

        assertThatThrownBy(() -> service.updateAvatar(EMAIL, "data:image/png;base64,"))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Ảnh đại diện rỗng");
    }

    @Test
    @DisplayName("Ảnh > 2 MB bị chặn trước khi ghi vào cột LONGTEXT")
    void updateAvatar_oversizedRejected() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user(1L)));
        byte[] big = new byte[2 * 1024 * 1024 + 1];
        System.arraycopy(pngBytes(), 0, big, 0, pngBytes().length);

        assertThatThrownBy(() -> service.updateAvatar(EMAIL, dataUrl("image/png", big)))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Ảnh đại diện tối đa 2 MB");
    }

    @Test
    @DisplayName("clearAvatar đặt null và lưu (avatar không bắt buộc)")
    void clearAvatar_setsNull() {
        User user = user(1L);
        user.setAvatar("data:image/png;base64,AAAA");
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));

        service.clearAvatar(EMAIL);

        assertThat(user.getAvatar()).isNull();
        verify(userRepository).save(user);
    }

    @Test
    @DisplayName("clearAvatar cho user không tồn tại: 401 như các API khác")
    void clearAvatar_unknownUserThrows() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.clearAvatar(EMAIL))
                .isInstanceOf(InvalidAccountException.class);
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private void assertRejected(String newPassword, String expectedMessage) {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user(1L)));
        when(passwordEncoder.matches("cu", "hash")).thenReturn(true);

        assertThatThrownBy(() -> service.changePassword(EMAIL, request("cu", newPassword)))
                .isInstanceOf(BadRequestException.class)
                .hasMessage(expectedMessage);

        verify(userRepository, never()).save(any());
        verifyNoInteractions(userSessionService);
    }

    private static ChangePasswordRequest request(String current, String next) {
        ChangePasswordRequest request = new ChangePasswordRequest();
        request.setCurrentPassword(current);
        request.setNewPassword(next);
        return request;
    }

    private static User user(Long id) {
        return User.builder().id(id).email(EMAIL).password("hash").build();
    }

    private static String dataUrl(String mime, byte[] bytes) {
        return "data:" + mime + ";base64," + Base64.getEncoder().encodeToString(bytes);
    }

    /** 8 byte chữ ký PNG là đủ để isPng() đúng; phần còn lại chỉ là đệm. */
    private static byte[] pngBytes() {
        return new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0};
    }

    private static byte[] jpegBytes() {
        return new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0, 0, 0};
    }

    private static byte[] webpBytes() {
        return new byte[]{'R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'E', 'B', 'P', 0, 0};
    }
}
