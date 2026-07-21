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
 * Unit tests for {@link LoggingFilter}, the gateway's request-timing/logging
 * filter.
 *
 * <p>The chain is mocked to complete empty. Since logging itself has no
 * asserted output, the observable contract is: the filter always continues the
 * chain, and it stamps a {@code startTime} exchange attribute that the
 * post-filter uses to compute request latency.</p>
 */
@DisplayName("LoggingFilter Tests")
class LoggingFilterTest {

    private LoggingFilter loggingFilter;
    private GatewayFilterChain filterChain;

    /** Fresh filter plus a pass-through chain mock. */
    @BeforeEach
    void setUp() {
        loggingFilter = new LoggingFilter();
        filterChain = mock(GatewayFilterChain.class);
        when(filterChain.filter(any())).thenReturn(Mono.empty());
    }

    /**
     * The filter must never swallow a request: it completes the chain AND
     * leaves a startTime attribute behind, which is the hook latency logging
     * depends on.
     */
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

    /**
     * The recorded startTime must be an actual wall-clock reading taken during
     * filtering, so it is bounded by the timestamps captured just before and
     * after the call — a hard-coded or zero value would make every latency
     * measurement meaningless.
     */
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

    /**
     * Timing/logging must be method-agnostic: GET, POST, PUT and DELETE all
     * get the startTime stamp, so latency metrics cover every verb rather than
     * silently skipping writes.
     */
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

    /**
     * The filter must run at (almost) highest precedence so it brackets the
     * whole chain — start time has to be captured before any other filter runs
     * for the measured latency to include their cost. Pinning the exact order
     * guards against a reorder that would silently under-report latency.
     */
    @Test
    @DisplayName("Should have correct filter order")
    void getOrder_shouldReturnCorrectOrder() {
        // When
        int order = loggingFilter.getOrder();

        // Then
        assertThat(order).isEqualTo(org.springframework.core.Ordered.HIGHEST_PRECEDENCE + 1);
    }
}

