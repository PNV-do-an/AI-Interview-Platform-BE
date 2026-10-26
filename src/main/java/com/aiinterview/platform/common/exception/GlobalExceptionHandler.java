package com.aiinterview.platform.common.exception;

import com.aiinterview.platform.model.entity.ErrorModel;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(InvalidPasswordException.class)
    public ResponseEntity<?> handleInvalidPassword(InvalidPasswordException ex, HttpServletRequest request) {
        ErrorModel error =  new ErrorModel(401,ex.getMessage(), request.getRequestURL().toString(), LocalDateTime.now(), // base information about bug
                new HashMap<>()); // if any information about bug

        return ResponseEntity .status(401) .body(error);
    }

    @ExceptionHandler(InvalidAccountException.class)
    public ResponseEntity<?> handleInvalidAccount(InvalidAccountException ex, HttpServletRequest request) {
        ErrorModel error = new ErrorModel(401,ex.getMessage(), request.getRequestURL().toString(), LocalDateTime.now(),
                new HashMap<>());
        return ResponseEntity.status(401).body(error);
    }

    @ExceptionHandler(InvalidAccountLockedException.class)
    public ResponseEntity<?> handleInvalidAccountLocked(InvalidAccountLockedException ex,HttpServletRequest request) {
        // Tài khoản bị admin khóa không có lockUntil; Map.of rejected null -> 500 khi đăng nhập.
        Map<String, Object> details = new HashMap<>();
        if (ex.getLockUntil() != null) {
            details.put("lockedUntil", ex.getLockUntil());
        }
        ErrorModel error = new ErrorModel(423,ex.getMessage(), request.getRequestURL().toString(), LocalDateTime.now(),
                details);
        return ResponseEntity.status(423).body(error);
    }

    @ExceptionHandler(EmailAlreadyRegisteredException.class)
    public ResponseEntity<?> handleEmailAlreadyRegistered(EmailAlreadyRegisteredException ex, HttpServletRequest request) {
        ErrorModel error = new ErrorModel(409, ex.getMessage(), request.getRequestURL().toString(), LocalDateTime.now(),
                new HashMap<>());
        return ResponseEntity.status(409).body(error);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<?> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        Map<String, Object> fieldErrors = new HashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.put(error.getField(), error.getDefaultMessage());
        }
        ErrorModel error = new ErrorModel(400, "Dữ liệu không hợp lệ", request.getRequestURL().toString(), LocalDateTime.now(),
                fieldErrors);
        return ResponseEntity.status(400).body(error);
    }

    /** Constraint violations on method parameters (@Validated + @Min/@Max on @RequestParam). */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<?> handleConstraintViolation(ConstraintViolationException ex, HttpServletRequest request) {
        Map<String, Object> details = new HashMap<>();
        ex.getConstraintViolations().forEach(v -> details.put(lastPathNode(v.getPropertyPath().toString()), v.getMessage()));
        ErrorModel error = new ErrorModel(400, "Dữ liệu không hợp lệ", request.getRequestURL().toString(),
                LocalDateTime.now(), details);
        return ResponseEntity.status(400).body(error);
    }

    /** Built-in controller parameter validation failure (Spring 6.1 path, no @Validated on the class). */
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<?> handleMethodValidation(HandlerMethodValidationException ex, HttpServletRequest request) {
        Map<String, Object> details = new HashMap<>();
        ex.getAllValidationResults().forEach(result -> result.getResolvableErrors().forEach(error -> {
            String[] codes = error.getCodes();
            String key = codes != null && codes.length > 0 ? codes[0] : "invalid";
            details.put(key, error.getDefaultMessage());
        }));
        ErrorModel error = new ErrorModel(400, "Dữ liệu không hợp lệ", request.getRequestURL().toString(),
                LocalDateTime.now(), details);
        return ResponseEntity.status(400).body(error);
    }

    /** A query/path parameter cannot be converted — e.g. status=FOO, role=ROLE_FOO. */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<?> handleTypeMismatch(MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        Map<String, Object> details = new HashMap<>();
        details.put("parameter", ex.getName());
        details.put("rejectedValue", ex.getValue());
        Class<?> targetType = ex.getRequiredType();
        if (targetType != null && targetType.isEnum()) {
            details.put("allowedValues", Arrays.stream(targetType.getEnumConstants())
                    .map(Object::toString).toList());
        }
        String message = "Tham số '" + ex.getName() + "' không hợp lệ"
                + (targetType != null && targetType.isEnum()
                ? ". Giá trị được phép: " + details.get("allowedValues") : "");
        ErrorModel error = new ErrorModel(400, message, request.getRequestURL().toString(),
                LocalDateTime.now(), details);
        return ResponseEntity.status(400).body(error);
    }

    private static String lastPathNode(String path) {
        int dot = path.lastIndexOf('.');
        return dot >= 0 ? path.substring(dot + 1) : path;
    }

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<?> handleBadRequest(BadRequestException ex, HttpServletRequest request) {
        ErrorModel error = new ErrorModel(400, ex.getMessage(), request.getRequestURL().toString(), LocalDateTime.now(),
                new HashMap<>());
        return ResponseEntity.status(400).body(error);
    }

    /** Settings values violate a business rule (spec 007: block at write with 422 + per-key messages). */
    @ExceptionHandler(SettingsValidationException.class)
    public ResponseEntity<?> handleSettingsValidation(SettingsValidationException ex, HttpServletRequest request) {
        ErrorModel error = new ErrorModel(422, ex.getMessage(), request.getRequestURL().toString(),
                LocalDateTime.now(), new HashMap<>(ex.getViolations()));
        return ResponseEntity.status(422).body(error);
    }

    /**
     * Đúng quyền chung nhưng không phải chủ bản ghi (spec 012: id phiên của người khác → 403).
     * Không dùng 404 vì sẽ xác nhận với caller rằng phiên đó tồn tại.
     */
    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<?> handleForbidden(ForbiddenException ex, HttpServletRequest request) {
        ErrorModel error = new ErrorModel(403, ex.getMessage(), request.getRequestURL().toString(), LocalDateTime.now(),
                new HashMap<>());
        return ResponseEntity.status(403).body(error);
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<?> handleNotFound(ResourceNotFoundException ex, HttpServletRequest request) {
        ErrorModel error = new ErrorModel(404, ex.getMessage(), request.getRequestURL().toString(), LocalDateTime.now(),
                new HashMap<>());
        return ResponseEntity.status(404).body(error);
    }

    @ExceptionHandler(MailDeliveryException.class)
    public ResponseEntity<?> handleMailDelivery(MailDeliveryException ex, HttpServletRequest request) {
        ErrorModel error = new ErrorModel(502, ex.getMessage(), request.getRequestURL().toString(), LocalDateTime.now(),
                new HashMap<>());
        return ResponseEntity.status(502).body(error);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorModel> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        boolean anonymous = authentication == null || authentication instanceof AnonymousAuthenticationToken;
        int status = anonymous ? 401 : 403;
        ErrorModel error = new ErrorModel(status, anonymous ? "Yêu cầu đăng nhập" : "Bạn không có quyền truy cập chức năng quản trị",
                request.getRequestURL().toString(), LocalDateTime.now(), new HashMap<>());
        return ResponseEntity.status(status).body(error);
    }

    /** Request body is missing or not valid JSON. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<?> handleUnreadableBody(HttpMessageNotReadableException ex, HttpServletRequest request) {
        ErrorModel error = new ErrorModel(400, "Dữ liệu gửi lên không đúng định dạng", request.getRequestURL().toString(),
                LocalDateTime.now(), new HashMap<>());
        return ResponseEntity.status(400).body(error);
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<?> handleOther(RuntimeException ex, HttpServletRequest request) {
        log.error("Unhandled error on {}", request.getRequestURL(), ex);
        ErrorModel error = new ErrorModel(500, "Lỗi hệ thống. Vui lòng thử lại sau.", request.getRequestURL().toString(), LocalDateTime.now(),
                new HashMap<>());
        return ResponseEntity.status(500).body(error);
    }
}