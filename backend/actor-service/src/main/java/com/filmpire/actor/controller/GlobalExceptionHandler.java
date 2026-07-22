package com.filmpire.actor.controller;

import com.filmpire.shared.dto.ApiResponse;
import com.filmpire.shared.exception.ResourceNotFoundException;
import com.filmpire.shared.exception.ServiceUnavailableException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;

/**
 * Maps exceptions on the NATIVE {@code /api/v1} endpoints to the shared
 * {@link ApiResponse} error envelope.
 *
 * <p>Scoped to the controller package so it does not intercept the facade's
 * own {@code @ExceptionHandler}s in {@code PersonFacadeController} — facade
 * errors must stay TMDB-shaped, native errors stay ApiResponse-shaped.</p>
 */
@RestControllerAdvice(basePackages = "com.filmpire.actor.controller")
@Order(1)
@Slf4j
public class GlobalExceptionHandler {

    /**
     * TMDB rejected the underlying fetch (e.g. unknown person id) → mirror
     * the upstream status with a native-shaped body.
     *
     * @param e upstream error captured by Spring's RestClient
     * @return error envelope with TMDB's status code
     */
    @ExceptionHandler(RestClientResponseException.class)
    public ResponseEntity<ApiResponse<Void>> handleUpstream(RestClientResponseException e) {
        return error(HttpStatus.valueOf(e.getStatusCode().value()),
            "TMDB rejected the request (HTTP " + e.getStatusCode().value() + ")");
    }

    /**
     * TMDB unreachable with no cached copy → 503.
     *
     * @param e network failure
     * @return 503 error envelope
     */
    @ExceptionHandler({ResourceAccessException.class, ServiceUnavailableException.class})
    public ResponseEntity<ApiResponse<Void>> handleUnavailable(Exception e) {
        log.error("Upstream unavailable: {}", e.getMessage());
        return error(HttpStatus.SERVICE_UNAVAILABLE, "Actor data is temporarily unavailable");
    }

    /**
     * Missing local resources → 404.
     *
     * @param e not-found error
     * @return 404 error envelope
     */
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotFound(ResourceNotFoundException e) {
        return error(HttpStatus.NOT_FOUND, e.getMessage());
    }

    /**
     * Anything unanticipated → 500 with a generic message (detail logged).
     *
     * @param e unexpected error
     * @return 500 error envelope
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception e) {
        log.error("Unhandled exception in actor-service", e);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred");
    }

    /**
     * Builds the standard error response.
     *
     * @param status  HTTP status
     * @param message client-safe description
     * @return error envelope response
     */
    private static ResponseEntity<ApiResponse<Void>> error(HttpStatus status, String message) {
        return ResponseEntity.status(status)
            .body(ApiResponse.error(message, status.value()));
    }
}
