package com.aiinterview.platform.common.exception;

import com.aiinterview.platform.model.entity.ErrorModel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.http.ResponseEntity;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();
    private final MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/login");

    @Test
    @DisplayName("An admin-locked account has no lockUntil: login must answer 423, not crash with 500")
    void adminLockWithoutLockUntilStillAnswers423() {
        ResponseEntity<?> response = handler.handleInvalidAccountLocked(
                new InvalidAccountLockedException(
                        "Tài khoản của bạn đã bị khóa bởi quản trị viên. Vui lòng liên hệ hỗ trợ.", null),
                request);

        assertEquals(423, response.getStatusCode().value());
        ErrorModel body = (ErrorModel) response.getBody();
        assertNotNull(body);
        assertTrue(body.getDetail().isEmpty());
    }

    @Test
    @DisplayName("A temporary brute-force block still reports when the account can be used again")
    void temporaryBlockCarriesLockedUntil() {
        LocalDateTime until = LocalDateTime.now().plusMinutes(15);

        ResponseEntity<?> response = handler.handleInvalidAccountLocked(
                new InvalidAccountLockedException("Tài khoản tạm thời bị khóa", until), request);

        assertEquals(423, response.getStatusCode().value());
        assertEquals(until, ((ErrorModel) response.getBody()).getDetail().get("lockedUntil"));
    }

    @Test
    @DisplayName("A 500 never echoes the internal exception message to the client")
    void internalErrorDoesNotLeakInternals() {
        ResponseEntity<?> response = handler.handleOther(
                new IllegalStateException("JDBC threw on table users where password='secret'"), request);

        assertEquals(500, response.getStatusCode().value());
        assertFalse(((ErrorModel) response.getBody()).getMessage().contains("secret"));
        assertFalse(((ErrorModel) response.getBody()).getMessage().contains("JDBC"));
    }
}
