package com.aiinterview.platform.common.exception;

import com.aiinterview.platform.model.entity.ErrorModel;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

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
        ErrorModel error = new ErrorModel(423,ex.getMessage(), request.getRequestURL().toString(), LocalDateTime.now(),
                new HashMap<>(
                        Map.of(
                                "lockedUntil", ex.getLockUntil()
                        )
                ));
        return ResponseEntity.status(423).body(error);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<?> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        Map<String, Object> fieldErrors = new HashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.put(error.getField(), error.getDefaultMessage());
        }
        ErrorModel error = new ErrorModel(400, "Validation failed", request.getRequestURL().toString(), LocalDateTime.now(),
                fieldErrors);
        return ResponseEntity.status(400).body(error);
    }

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<?> handleBadRequest(BadRequestException ex, HttpServletRequest request) {
        ErrorModel error = new ErrorModel(400, ex.getMessage(), request.getRequestURL().toString(), LocalDateTime.now(),
                new HashMap<>());
        return ResponseEntity.status(400).body(error);
    }

    @ExceptionHandler(AIServiceException.class)
    public ResponseEntity<?> handleAIService(AIServiceException ex, HttpServletRequest request) {
        ErrorModel error = new ErrorModel(502, ex.getMessage(), request.getRequestURL().toString(), LocalDateTime.now(),
                new HashMap<>());
        return ResponseEntity.status(502).body(error);
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<?> handleNotFound(ResourceNotFoundException ex, HttpServletRequest request) {
        ErrorModel error = new ErrorModel(404, ex.getMessage(), request.getRequestURL().toString(), LocalDateTime.now(),
                new HashMap<>());
        return ResponseEntity.status(404).body(error);
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<?> handleOther(RuntimeException ex, HttpServletRequest request) {
        ex.printStackTrace();
        ErrorModel error = new ErrorModel(500, "Internal server error: " + ex.getMessage(), request.getRequestURL().toString(), LocalDateTime.now(),
                new HashMap<>());
        return ResponseEntity.status(500).body(error);
    }
}