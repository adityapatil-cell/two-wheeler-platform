package com.twowheeler.common.exception;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.List;

/**
 * Standard error response shape returned by every service.
 * Matches the OpenAPI contract defined in Phase 1.
 *
 * Example response:
 * {
 *   "timestamp": "2024-06-01T10:00:00Z",
 *   "status": 404,
 *   "errorCode": "REPAIR_NOT_FOUND",
 *   "message": "Repair order not found with id: uuid",
 *   "traceId": "abc-123-xyz",
 *   "validationErrors": null
 * }
 */
@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiErrorResponse {

    private final Instant timestamp;
    private final int status;
    private final String errorCode;
    private final String message;
    private final String traceId;

    // Only populated for 400 validation errors
    private final List<ValidationError> validationErrors;

    @Getter
    @Builder
    public static class ValidationError {
        private final String field;
        private final String message;
        private final Object rejectedValue;
    }
}
