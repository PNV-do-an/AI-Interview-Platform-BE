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
 */
class RegisterRequestValidationTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void openValidator() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void closeValidator() {
        factory.close();
    }

    private Set<ConstraintViolation<RegisterRequest>> violations(String password) {
        RegisterRequest request = new RegisterRequest();
        request.setFullName("Jane Doe");
        request.setEmail("new.candidate@example.com");
        request.setPassword(password);
        return validator.validate(request);
    }

    @Test
    void weakPasswordsAreNotBlockedByDtoSoTheServiceCanRecordTheFailure() {
        for (String weak : new String[]{"weak", "12345678", "alllowercase", "NoDigitHere!", "Sh0rt!A"}) {
            Set<ConstraintViolation<RegisterRequest>> result = violations(weak);
            assertTrue(result.isEmpty(), "Mật khẩu yếu '" + weak + "' phải xuống được service, nhận về " + result);
        }
    }

    @Test
    void missingOrMalformedStructuralFieldsAreStillRejectedBeforeTheService() {
        Set<ConstraintViolation<RegisterRequest>> nullPassword = violations(null);
        assertEquals(1, nullPassword.size());
        assertEquals("Password is required", nullPassword.iterator().next().getMessage());

        Set<ConstraintViolation<RegisterRequest>> blankPassword = violations("   ");
        assertEquals(1, blankPassword.size());
        assertEquals("Password is required", blankPassword.iterator().next().getMessage());

        RegisterRequest request = new RegisterRequest();
        request.setFullName(" ");
        request.setEmail("not-an-email");
        request.setPassword("ProbePass!23");
        Set<String> messages = validator.validate(request).stream()
                .map(ConstraintViolation::getMessage).collect(java.util.stream.Collectors.toSet());
        assertEquals(2, messages.size(), "Chỉ 2 lỗi cấu trúc được kỳ vọng, nhận về " + messages);
        assertTrue(messages.contains("Full name is required"));
        assertTrue(messages.contains("Invalid email format"));
    }
}
