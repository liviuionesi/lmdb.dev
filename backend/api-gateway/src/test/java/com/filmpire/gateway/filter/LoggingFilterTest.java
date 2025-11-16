package com.filmpire.gateway.filter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for LoggingFilter.
 */
@DisplayName("LoggingFilter Tests")
class LoggingFilterTest {

    private LoggingFilter loggingFilter;
    private GatewayFilterChain filterChain;

    @BeforeEach
    void setUp() {
        loggingFilter = new LoggingFilter();
        filterChain = mock(GatewayFilterChain.class);
        when(filterChain.filter(any())).thenReturn(Mono.empty());
    }

    @Test
    @DisplayName("Should continue filter chain and log request")
    void filter_shouldContinueFilterChain() {
        // Given
        MockServerHttpRequest request = Objects.requireNonNull(
                MockServerHttpRequest.get("/api/v1/movies").build(),
                "Request must not be null");
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        // When
        Mono<Void> result = loggingFilter.filter(exchange, filterChain);

        // Then
        StepVerifier.create(result)
                .verifyComplete();
        
        // Verify startTime attribute was set
        Long startTime = exchange.getAttribute("startTime");
        assertThat(startTime).isNotNull();
    }

    @Test
    @DisplayName("Should record request start time")
    void filter_shouldRecordStartTime() {
        // Given
        MockServerHttpRequest request = Objects.requireNonNull(
                MockServerHttpRequest.post("/api/v1/auth/login").build(),
                "Request must not be null");
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        long beforeFilter = System.currentTimeMillis();

        // When
        Mono<Void> result = loggingFilter.filter(exchange, filterChain);

        // Then
        StepVerifier.create(result)
                .verifyComplete();
        
        Long startTime = exchange.getAttribute("startTime");
        assertThat(startTime)
                .isNotNull()
                .isGreaterThanOrEqualTo(beforeFilter)
                .isLessThanOrEqualTo(System.currentTimeMillis());
    }

    @Test
    @DisplayName("Should handle different HTTP methods")
    void filter_shouldHandleDifferentHttpMethods() {
        // Given
        MockServerHttpRequest[] requests = {
                Objects.requireNonNull(MockServerHttpRequest.get("/api/v1/movies").build(), "Request must not be null"),
                Objects.requireNonNull(MockServerHttpRequest.post("/api/v1/movies").build(), "Request must not be null"),
                Objects.requireNonNull(MockServerHttpRequest.put("/api/v1/movies/1").build(), "Request must not be null"),
                Objects.requireNonNull(MockServerHttpRequest.delete("/api/v1/movies/1").build(), "Request must not be null")
        };

        // When & Then
        for (MockServerHttpRequest request : requests) {
            MockServerHttpRequest nonNullRequest = Objects.requireNonNull(request, "Request must not be null");
            MockServerWebExchange exchange = MockServerWebExchange.from(nonNullRequest);
            Mono<Void> result = loggingFilter.filter(exchange, filterChain);
            
            StepVerifier.create(result)
                    .verifyComplete();
            
            Long startTime = exchange.getAttribute("startTime");
            assertThat(startTime).isNotNull();
        }
    }

    @Test
    @DisplayName("Should have correct filter order")
    void getOrder_shouldReturnCorrectOrder() {
        // When
        int order = loggingFilter.getOrder();

        // Then
        assertThat(order).isEqualTo(org.springframework.core.Ordered.HIGHEST_PRECEDENCE + 1);
    }
}

