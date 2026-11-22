package com.aiinterview.platform.model.dto.request;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Hợp đồng phân lớp: DTO chỉ chặn tính hợp lệ về cấu trúc (có mặt / đúng định dạng).
 * Rule độ mạnh mật khẩu phải nằm ở RegisterInterfaceImpl để request yếu xuống được service,
 * nơi duy nhất ghi register_attempts và bật được ngưỡng rate-limit.
 *
 * <p>Mỗi test chia 3 khối comment: <b>Arrange</b> → <b>Act</b> → <b>Assert</b>.</p>
 */
class RegisterRequestValidationTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void openValidator() {
        // Dựng validator thật (Hibernate Validator) một lần cho cả class
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void closeValidator() {
        // Đóng factory để giải phóng tài nguyên sau khi hết class
        factory.close();
    }

    /** Request hợp lệ về cấu trúc, chỉ thay mật khẩu để test đúng một trục. */
    private Set<ConstraintViolation<RegisterRequest>> violations(String password) {
        RegisterRequest request = new RegisterRequest();
        request.setFullName("Jane Doe");
        request.setEmail("new.candidate@example.com");
        request.setPassword(password);
        return validator.validate(request);
    }

    @Test
    void weakPasswordsAreNotBlockedByDtoSoTheServiceCanRecordTheFailure() {
        // Arrange: 5 mật khẩu yếu ở 5 kiểu khác nhau (quá ngắn, toàn số, toàn chữ thường,
        // thiếu số, thiếu độ dài) — tất cả đều "đúng cấu trúc" nên phải lọt qua DTO
        String[] weakPasswords = {"weak", "12345678", "alllowercase", "NoDigitHere!", "Sh0rt!A"};

        // Act & Assert: DTO không được chặn, để service còn ghi register_attempts và đếm rate-limit
        for (String weak : weakPasswords) {
            Set<ConstraintViolation<RegisterRequest>> result = violations(weak);
            assertTrue(result.isEmpty(), "Mật khẩu yếu '" + weak + "' phải xuống được service, nhận về " + result);
        }
    }

    @Test
    void missingOrMalformedStructuralFieldsAreStillRejectedBeforeTheService() {
        // Arrange: password null và password toàn khoảng trắng — hai kiểu "thiếu" khác nhau
        Set<ConstraintViolation<RegisterRequest>> nullPassword = violations(null);
        Set<ConstraintViolation<RegisterRequest>> blankPassword = violations("   ");

        // Act & Assert: cả hai chỉ sinh đúng 1 lỗi "bắt buộc", không nhân đôi thông báo
        assertEquals(1, nullPassword.size());
        assertEquals("Password is required", nullPassword.iterator().next().getMessage());
        assertEquals(1, blankPassword.size());
        assertEquals("Password is required", blankPassword.iterator().next().getMessage());

        // Arrange: các trường cấu trúc khác cũng bị chặn ngay tại DTO (tên toàn khoảng trắng, email sai định dạng)
        RegisterRequest request = new RegisterRequest();
        request.setFullName(" ");
        request.setEmail("not-an-email");
        request.setPassword("ProbePass!23");

        // Act
        Set<String> messages = validator.validate(request).stream()
                .map(ConstraintViolation::getMessage).collect(Collectors.toSet());

        // Assert: đúng 2 lỗi cấu trúc, không lẫn lỗi mật khẩu (mật khẩu ở đây hợp lệ)
        assertEquals(2, messages.size(), "Chỉ 2 lỗi cấu trúc được kỳ vọng, nhận về " + messages);
        assertTrue(messages.contains("Full name is required"));
        assertTrue(messages.contains("Invalid email format"));
    }
}
