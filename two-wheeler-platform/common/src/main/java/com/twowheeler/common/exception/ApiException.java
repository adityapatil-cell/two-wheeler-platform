package com.twowheeler.common.exception;

import org.springframework.http.HttpStatus;
import lombok.Getter;

/**
 * Base runtime exception for all Two Wheeler platform services.
 *
 * Usage:
 *   throw new ApiException(HttpStatus.NOT_FOUND, "REPAIR_NOT_FOUND", "Repair order not found");
 *
 * GlobalExceptionHandler catches this and maps it to ApiErrorResponse.
 */
@Getter
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String errorCode;

    public ApiException(HttpStatus status, String errorCode, String message) {
        super(message);
        this.status = status;
        this.errorCode = errorCode;
    }

    public ApiException(HttpStatus status, String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.status = status;
        this.errorCode = errorCode;
    }

    // ─── Common factory methods used across all services ───────────────────

    public static ApiException notFound(String resource, String id) {
        return new ApiException(
            HttpStatus.NOT_FOUND,
            resource.toUpperCase() + "_NOT_FOUND",
            resource + " not found with id: " + id
        );
    }

    public static ApiException badRequest(String errorCode, String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, errorCode, message);
    }

    public static ApiException forbidden(String message) {
        return new ApiException(HttpStatus.FORBIDDEN, "ACCESS_DENIED", message);
    }

    public static ApiException conflict(String errorCode, String message) {
        return new ApiException(HttpStatus.CONFLICT, errorCode, message);
    }

    public static ApiException internalError(String message) {
        return new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", message);
    }
}
