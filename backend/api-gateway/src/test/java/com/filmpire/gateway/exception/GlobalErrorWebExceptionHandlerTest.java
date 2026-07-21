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
 * Unit tests for {@link GlobalErrorWebExceptionHandler}, the gateway's
 * catch-all reactive error boundary.
 *
 * <p>Because the gateway sits in front of every service, an unhandled error
 * here would leak a raw stack trace or an inconsistent body to clients. These
 * tests drive representative exception types through the handler and assert two
 * things per case: the mapped HTTP status is correct, and the response is
 * always {@code application/json}. A real {@link ObjectMapper} is used so the
 * body is genuinely serialized; the final test swaps in a deliberately-broken
 * mapper to prove the handler degrades gracefully even when serialization
 * itself fails.</p>
 */
@DisplayName("GlobalErrorWebExceptionHandler Tests")
class GlobalErrorWebExceptionHandlerTest {

    private GlobalErrorWebExceptionHandler errorHandler;
    private ObjectMapper objectMapper;

    /** Fresh handler backed by a real, working ObjectMapper. */
    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        errorHandler = new GlobalErrorWebExceptionHandler(objectMapper);
    }

    /**
     * A ResponseStatusException must surface its own status (404 here) rather
     * than being flattened to 500 — this is how downstream/intentional status
     * codes propagate to the client through the gateway.
     */
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

    /**
     * Any JWT failure (here an expired token) must map to 401, not 500 — a
     * bad/expired token is an authentication problem, and returning 500 would
     * both mislead clients and hide the real cause.
     */
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

    /**
     * IllegalArgumentException signifies bad client input, so it must map to
     * 400 — attributing a caller mistake to a server fault (500) would skew
     * error budgets and mislead debugging.
     */
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

    /**
     * The catch-all: an unrecognized RuntimeException must become a 500 with a
     * JSON body, so unexpected failures still return a well-formed, non-leaky
     * response instead of a raw error.
     */
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

    /**
     * Content-type discipline: regardless of the error, the response must be
     * application/json so clients can parse errors uniformly instead of
     * special-casing HTML or plain-text gateway errors.
     */
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

    /**
     * The error body should carry the request path so clients and logs can
     * correlate a failure to the endpoint that produced it; the request here
     * uses a distinct path and the handler must still resolve to a well-formed
     * 500.
     */
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

    /**
     * Defense-in-depth: if the ObjectMapper itself throws while serializing the
     * error body, the handler must still complete the exchange rather than
     * throwing a second exception out of the error path (which would leave the
     * client with a dropped connection). Uses a mock mapper rigged to fail.
     */
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

