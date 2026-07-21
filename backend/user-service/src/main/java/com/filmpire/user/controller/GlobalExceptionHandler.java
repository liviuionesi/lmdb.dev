package com.filmpire.user.controller;

import com.filmpire.shared.dto.ApiResponse;
import com.filmpire.shared.exception.BusinessException;
import com.filmpire.shared.exception.ResourceNotFoundException;
import com.filmpire.shared.exception.UnauthorizedException;
import com.filmpire.shared.exception.ValidationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

/**
 * Maps exceptions to the shared {@link ApiResponse} error envelope with the
 * appropriate HTTP status, keeping error responses consistent across the
 * user-service API (and with the other Filmpire services).
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    /**
     * Bean-validation failures on request bodies → 400 with a compact
     * field-by-field summary.
     *
     * @param e validation failure raised by {@code @Valid}
     * @return 400 error envelope listing the offending fields
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidBody(MethodArgumentNotValidException e) {
        String details = e.getBindingResult().getFieldErrors().stream()
            .map(err -> err.getField() + ": " + err.getDefaultMessage())
            .collect(Collectors.joining("; "));
        return error(HttpStatus.BAD_REQUEST, "Validation failed — " + details);
    }

    /**
     * Domain validation failures (duplicate username/email, …) → 400.
     *
     * @param e domain validation error
     * @return 400 error envelope
     */
    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(ValidationException e) {
        return error(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    /**
     * Authentication failures (bad credentials, invalid/expired refresh
     * token, wrong current password) → 401.
     *
     * @param e authentication error
     * @return 401 error envelope
     */
    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnauthorized(UnauthorizedException e) {
        return error(HttpStatus.UNAUTHORIZED, e.getMessage());
    }

    /**
     * Missing resources (account deleted after token issue) → 404.
     *
     * @param e not-found error
     * @return 404 error envelope
     */
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotFound(ResourceNotFoundException e) {
        return error(HttpStatus.NOT_FOUND, e.getMessage());
    }

    /**
     * Business-rule conflicts → 409.
     *
     * @param e business error
     * @return 409 error envelope
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusiness(BusinessException e) {
        return error(HttpStatus.CONFLICT, e.getMessage());
    }

    /**
     * Anything unanticipated → 500 with a generic message; the detail goes
     * to the log only (never leak internals to clients).
     *
     * @param e unexpected error
     * @return 500 error envelope
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception e) {
        log.error("Unhandled exception in user-service", e);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred");
    }

    /**
     * Builds the standard error response.
     *
     * @param status  HTTP status to return
     * @param message client-safe error description
     * @return response entity with the shared error envelope
     */
    private static ResponseEntity<ApiResponse<Void>> error(HttpStatus status, String message) {
        return ResponseEntity.status(status)
            .body(ApiResponse.error(message, status.value()));
    }
}
