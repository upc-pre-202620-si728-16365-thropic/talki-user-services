package com.thropic.talki.identity.interfaces.rest;

import com.thropic.talki.identity.domain.exception.DuplicateEmailException;
import com.thropic.talki.identity.domain.exception.InvalidAcademicSegmentException;
import com.thropic.talki.identity.domain.exception.InvalidCredentialsException;
import com.thropic.talki.identity.domain.exception.InvalidPasswordException;
import com.thropic.talki.identity.domain.exception.TooManyLoginAttemptsException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

/**
 * Manejo centralizado de errores de autenticación. Sin esto, las excepciones
 * de AuthService salen como 500 genéricos.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(DuplicateEmailException.class)
    public ResponseEntity<Map<String, Object>> handleDuplicateEmail(DuplicateEmailException e) {
        return build(HttpStatus.CONFLICT, e.getMessage());
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidCredentials(InvalidCredentialsException e) {
        // Mensaje único y genérico: sin email ni causa.
        return build(HttpStatus.UNAUTHORIZED, e.getMessage());
    }

    @ExceptionHandler(InvalidPasswordException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidPassword(InvalidPasswordException e) {
        return build(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(InvalidAcademicSegmentException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidAcademicSegment(InvalidAcademicSegmentException e) {
        return build(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(TooManyLoginAttemptsException.class)
    public ResponseEntity<Map<String, Object>> handleTooManyAttempts(TooManyLoginAttemptsException e) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header("Retry-After", String.valueOf(e.getRetryAfterSeconds()))
                .body(Map.of(
                        "status", HttpStatus.TOO_MANY_REQUESTS.value(),
                        "error", HttpStatus.TOO_MANY_REQUESTS.getReasonPhrase(),
                        "message", e.getMessage(),
                        "retryAfterSeconds", e.getRetryAfterSeconds()
                ));
    }

    private ResponseEntity<Map<String, Object>> build(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(Map.of(
                "status", status.value(),
                "error", status.getReasonPhrase(),
                "message", message
        ));
    }
}
