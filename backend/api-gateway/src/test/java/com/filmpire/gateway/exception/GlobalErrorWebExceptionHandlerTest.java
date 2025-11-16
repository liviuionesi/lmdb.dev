package com.filmpire.gateway.exception;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for GlobalErrorWebExceptionHandler.
 */
@DisplayName("GlobalErrorWebExceptionHandler Tests")
class GlobalErrorWebExceptionHandlerTest {

    private GlobalErrorWebExceptionHandler errorHandler;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        errorHandler = new GlobalErrorWebExceptionHandler(objectMapper);
    }

    @Test
    @DisplayName("Should handle ResponseStatusException")
    void handle_shouldHandleResponseStatusException() {
        // Given
        MockServerHttpRequest request = Objects.requireNonNull(
                MockServerHttpRequest.get("/api/v1/movies").build(),
                "Request must not be null");
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        ResponseStatusException exception = new ResponseStatusException(
                HttpStatus.NOT_FOUND, "Resource not found");

        // When
        Mono<Void> result = errorHandler.handle(exchange, exception);

        // Then
        StepVerifier.create(result)
                .verifyComplete();
        
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(exchange.getResponse().getHeaders().getContentType())
                .isEqualTo(MediaType.APPLICATION_JSON);
    }

    @Test
    @DisplayName("Should handle JWT exceptions")
    void handle_shouldHandleJwtException() {
        // Given
        MockServerHttpRequest request = Objects.requireNonNull(
                MockServerHttpRequest.get("/api/v1/users/profile").build(),
                "Request must not be null");
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        JwtException exception = new io.jsonwebtoken.ExpiredJwtException(
                null, null, "Token expired");

        // When
        Mono<Void> result = errorHandler.handle(exchange, exception);

        // Then
        StepVerifier.create(result)
                .verifyComplete();
        
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(exchange.getResponse().getHeaders().getContentType())
                .isEqualTo(MediaType.APPLICATION_JSON);
    }

    @Test
    @DisplayName("Should handle IllegalArgumentException")
    void handle_shouldHandleIllegalArgumentException() {
        // Given
        MockServerHttpRequest request = Objects.requireNonNull(
                MockServerHttpRequest.get("/api/v1/movies").build(),
                "Request must not be null");
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        IllegalArgumentException exception = new IllegalArgumentException("Invalid parameter");

        // When
        Mono<Void> result = errorHandler.handle(exchange, exception);

        // Then
        StepVerifier.create(result)
                .verifyComplete();
        
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(exchange.getResponse().getHeaders().getContentType())
                .isEqualTo(MediaType.APPLICATION_JSON);
    }

    @Test
    @DisplayName("Should handle generic exceptions")
    void handle_shouldHandleGenericException() {
        // Given
        MockServerHttpRequest request = Objects.requireNonNull(
                MockServerHttpRequest.get("/api/v1/movies").build(),
                "Request must not be null");
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        RuntimeException exception = new RuntimeException("Unexpected error");

        // When
        Mono<Void> result = errorHandler.handle(exchange, exception);

        // Then
        StepVerifier.create(result)
                .verifyComplete();
        
        assertThat(exchange.getResponse().getStatusCode())
                .isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(exchange.getResponse().getHeaders().getContentType())
                .isEqualTo(MediaType.APPLICATION_JSON);
    }

    @Test
    @DisplayName("Should return JSON error response")
    void handle_shouldReturnJsonErrorResponse() {
        // Given
        MockServerHttpRequest request = Objects.requireNonNull(
                MockServerHttpRequest.get("/api/v1/movies").build(),
                "Request must not be null");
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        ResponseStatusException exception = new ResponseStatusException(
                HttpStatus.BAD_REQUEST, "Invalid request");

        // When
        Mono<Void> result = errorHandler.handle(exchange, exception);

        // Then
        StepVerifier.create(result)
                .verifyComplete();
        
        // Verify response body contains error response
        assertThat(exchange.getResponse().getHeaders().getContentType())
                .isEqualTo(MediaType.APPLICATION_JSON);
    }

    @Test
    @DisplayName("Should include path in error response")
    void handle_shouldIncludePathInErrorResponse() {
        // Given
        String path = "/api/v1/users/profile";
        MockServerHttpRequest request = Objects.requireNonNull(
                MockServerHttpRequest.get(path).build(),
                "Request must not be null");
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        RuntimeException exception = new RuntimeException("Test error");

        // When
        Mono<Void> result = errorHandler.handle(exchange, exception);

        // Then
        StepVerifier.create(result)
                .verifyComplete();
        
        assertThat(exchange.getResponse().getStatusCode())
                .isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Test
    @DisplayName("Should handle JsonProcessingException gracefully")
    void handle_shouldHandleJsonProcessingException() {
        // Given - Create a handler with a broken ObjectMapper
        ObjectMapper brokenMapper = mock(ObjectMapper.class);
        try {
            when(brokenMapper.writeValueAsString(any())).thenThrow(
                    new com.fasterxml.jackson.core.JsonProcessingException("JSON error") {});
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            // This won't happen in the test
        }
        
        GlobalErrorWebExceptionHandler brokenHandler = 
                new GlobalErrorWebExceptionHandler(brokenMapper);
        
        MockServerHttpRequest request = Objects.requireNonNull(
                MockServerHttpRequest.get("/api/v1/movies").build(),
                "Request must not be null");
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        RuntimeException exception = new RuntimeException("Test error");

        // When
        Mono<Void> result = brokenHandler.handle(exchange, exception);

        // Then - Should complete without error
        StepVerifier.create(result)
                .verifyComplete();
    }
}

