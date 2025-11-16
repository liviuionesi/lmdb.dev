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
 * Unit tests for FallbackController.
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("FallbackController Tests")
class FallbackControllerTest {

    @Autowired
    private FallbackController fallbackController;

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

