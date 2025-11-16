package com.filmpire.shared.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Detailed error response for exception handling.
 * Provides comprehensive error information including validation errors and debugging details.
 *
 * @author Filmpire Development Team
 * @version 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ErrorResponse {

    /**
     * HTTP status code
     */
    private int status;

    /**
     * Error code for client-side handling
     */
    private String errorCode;

    /**
     * Human-readable error message
     */
    private String message;

    /**
     * Detailed error description (optional, for debugging)
     */
    private String details;

    /**
     * Request path where the error occurred
     */
    private String path;

    /**
     * Timestamp when the error occurred
     */
    @Builder.Default
    private LocalDateTime timestamp = LocalDateTime.now();

    /**
     * Field-level validation errors (for validation failures)
     */
    private Map<String, String> fieldErrors;

    /**
     * Stack trace (only in development mode)
     */
    private List<String> stackTrace;

    /**
     * Additional metadata about the error
     */
    private Map<String, Object> metadata;

    /**
     * Creates a simple error response
     *
     * @param status    HTTP status code
     * @param errorCode error code
     * @param message   error message
     * @param path      request path
     * @return ErrorResponse
     */
    public static ErrorResponse of(int status, String errorCode, String message, String path) {
        return ErrorResponse.builder()
                .status(status)
                .errorCode(errorCode)
                .message(message)
                .path(path)
                .timestamp(LocalDateTime.now())
                .build();
    }

    /**
     * Creates an error response with validation errors
     *
     * @param status      HTTP status code
     * @param errorCode   error code
     * @param message     error message
     * @param path        request path
     * @param fieldErrors field-level validation errors
     * @return ErrorResponse with validation errors
     */
    public static ErrorResponse withValidationErrors(
            int status,
            String errorCode,
            String message,
            String path,
            Map<String, String> fieldErrors) {
        return ErrorResponse.builder()
                .status(status)
                .errorCode(errorCode)
                .message(message)
                .path(path)
                .fieldErrors(fieldErrors)
                .timestamp(LocalDateTime.now())
                .build();
    }
}



