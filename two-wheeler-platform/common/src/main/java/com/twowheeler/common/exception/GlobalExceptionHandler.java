package com.twowheeler.common.exception;

import io.micrometer.tracing.Tracer;
import jakarta.validation.ConstraintViolationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.List;

/**
 * Global exception handler — shared across all services via the common module.
 *
 * Catches:
 *   ApiException          → maps to its own status + errorCode
 *   Validation errors     → 400 with field-level details
 *   AccessDeniedException → 403
 *   AuthenticationException → 401
 *   Any other Throwable   → 500 (message hidden from client, logged fully)
 *
 * TraceId is injected into every error response so support teams
 * can find the full request in Kibana using one ID.
 */
@Slf4j
@RestControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler {

    private final Tracer tracer;

    // ─── Business exceptions ────────────────────────────────────────────────

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiErrorResponse> handleApiException(ApiException ex) {
        log.warn("ApiException [{}] {}: {}", ex.getStatus(), ex.getErrorCode(), ex.getMessage());
        return ResponseEntity
            .status(ex.getStatus())
            .body(buildResponse(ex.getStatus().value(), ex.getErrorCode(), ex.getMessage(), null));
    }

    // ─── Validation errors (@Valid on request bodies) ───────────────────────

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        List<ApiErrorResponse.ValidationError> errors = ex.getBindingResult()
            .getFieldErrors()
            .stream()
            .map(fe -> ApiErrorResponse.ValidationError.builder()
                .field(fe.getField())
                .message(fe.getDefaultMessage())
                .rejectedValue(fe.getRejectedValue())
                .build())
            .toList();

        log.warn("Validation failed: {} field errors", errors.size());
        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(buildResponse(400, "VALIDATION_FAILED", "Request validation failed", errors));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleConstraintViolation(ConstraintViolationException ex) {
        List<ApiErrorResponse.ValidationError> errors = ex.getConstraintViolations()
            .stream()
            .map(cv -> ApiErrorResponse.ValidationError.builder()
                .field(cv.getPropertyPath().toString())
                .message(cv.getMessage())
                .rejectedValue(cv.getInvalidValue())
                .build())
            .toList();

        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(buildResponse(400, "CONSTRAINT_VIOLATION", "Constraint violation", errors));
    }

    // ─── Security exceptions ────────────────────────────────────────────────

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiErrorResponse> handleAccessDenied(AccessDeniedException ex) {
        log.warn("Access denied: {}", ex.getMessage());
        return ResponseEntity
            .status(HttpStatus.FORBIDDEN)
            .body(buildResponse(403, "ACCESS_DENIED", "You do not have permission to access this resource", null));
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiErrorResponse> handleAuthentication(AuthenticationException ex) {
        log.warn("Authentication failed: {}", ex.getMessage());
        return ResponseEntity
            .status(HttpStatus.UNAUTHORIZED)
            .body(buildResponse(401, "UNAUTHORIZED", "Authentication required", null));
    }

    // ─── Catch-all — never expose internal details to client ────────────────

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleGeneric(Exception ex) {
        log.error("Unhandled exception", ex);
        return ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(buildResponse(500, "INTERNAL_ERROR", "An unexpected error occurred", null));
    }

    // ─── Helper ─────────────────────────────────────────────────────────────

    private ApiErrorResponse buildResponse(int status, String errorCode,
                                           String message,
                                           List<ApiErrorResponse.ValidationError> validationErrors) {
        String traceId = null;
        if (tracer.currentSpan() != null) {
            traceId = tracer.currentSpan().context().traceId();
        }
        return ApiErrorResponse.builder()
            .timestamp(Instant.now())
            .status(status)
            .errorCode(errorCode)
            .message(message)
            .traceId(traceId)
            .validationErrors(validationErrors)
            .build();
    }
}
