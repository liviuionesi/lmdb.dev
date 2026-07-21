package com.filmpire.gateway.controller;

import com.filmpire.shared.dto.ApiResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link FallbackController}, the destination Resilience4j
 * routes to when a downstream service's circuit breaker is open.
 *
 * <p>Each per-service fallback must return a consistent, client-friendly
 * degraded response: HTTP 503 in the shared {@link ApiResponse} envelope with
 * {@code success=false} and a message naming the affected service. These tests
 * assert that contract per service so a failing dependency yields a clear
 * "temporarily unavailable" rather than a raw gateway error or a hang.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("FallbackController Tests")
class FallbackControllerTest {

    @Autowired
    private FallbackController fallbackController;

    /**
     * Movie-service fallback must be a 503 ApiResponse naming "Movie Service",
     * so a tripped movie-service breaker degrades gracefully.
     */
    @Test
    @DisplayName("Should return service unavailable for movie service fallback")
    void movieServiceFallback_shouldReturnServiceUnavailable() {
        // When
        ResponseEntity<ApiResponse<Void>> response = fallbackController.movieServiceFallback();

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        ApiResponse<Void> body = Objects.requireNonNull(response.getBody(), "Response body must not be null");
        assertThat(body.isSuccess()).isFalse();
        assertThat(body.getMessage()).contains("Movie Service");
    }

    /**
     * User-service fallback must be a 503 ApiResponse naming "User Service".
     */
    @Test
    @DisplayName("Should return service unavailable for user service fallback")
    void userServiceFallback_shouldReturnServiceUnavailable() {
        // When
        ResponseEntity<ApiResponse<Void>> response = fallbackController.userServiceFallback();

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        ApiResponse<Void> body = Objects.requireNonNull(response.getBody(), "Response body must not be null");
        assertThat(body.isSuccess()).isFalse();
        assertThat(body.getMessage()).contains("User Service");
    }

    /**
     * Auth-service fallback must be a 503 ApiResponse naming "Authentication
     * Service" — the message wording differs from the route name, so this
     * pins the human-facing text.
     */
    @Test
    @DisplayName("Should return service unavailable for auth service fallback")
    void authServiceFallback_shouldReturnServiceUnavailable() {
        // When
        ResponseEntity<ApiResponse<Void>> response = fallbackController.authServiceFallback();

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        ApiResponse<Void> body = Objects.requireNonNull(response.getBody(), "Response body must not be null");
        assertThat(body.isSuccess()).isFalse();
        assertThat(body.getMessage()).contains("Authentication Service");
    }

    /**
     * Actor-service fallback must be a 503 ApiResponse naming "Actor Service".
     */
    @Test
    @DisplayName("Should return service unavailable for actor service fallback")
    void actorServiceFallback_shouldReturnServiceUnavailable() {
        // When
        ResponseEntity<ApiResponse<Void>> response = fallbackController.actorServiceFallback();

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        ApiResponse<Void> body = Objects.requireNonNull(response.getBody(), "Response body must not be null");
        assertThat(body.isSuccess()).isFalse();
        assertThat(body.getMessage()).contains("Actor Service");
    }

    /**
     * AI-service fallback must be a 503 ApiResponse naming "AI Service".
     */
    @Test
    @DisplayName("Should return service unavailable for AI service fallback")
    void aiServiceFallback_shouldReturnServiceUnavailable() {
        // When
        ResponseEntity<ApiResponse<Void>> response = fallbackController.aiServiceFallback();

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        ApiResponse<Void> body = Objects.requireNonNull(response.getBody(), "Response body must not be null");
        assertThat(body.isSuccess()).isFalse();
        assertThat(body.getMessage()).contains("AI Service");
    }

    /**
     * Media-service fallback must be a 503 ApiResponse naming "Media Service".
     */
    @Test
    @DisplayName("Should return service unavailable for media service fallback")
    void mediaServiceFallback_shouldReturnServiceUnavailable() {
        // When
        ResponseEntity<ApiResponse<Void>> response = fallbackController.mediaServiceFallback();

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        ApiResponse<Void> body = Objects.requireNonNull(response.getBody(), "Response body must not be null");
        assertThat(body.isSuccess()).isFalse();
        assertThat(body.getMessage()).contains("Media Service");
    }
}

