package com.filmpire.gateway.filter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.lang.reflect.Field;
import java.net.InetSocketAddress;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for GlobalRateLimitFilter.
 */
@DisplayName("GlobalRateLimitFilter Tests")
@SuppressWarnings("null")
class GlobalRateLimitFilterTest {

    private GlobalRateLimitFilter globalRateLimitFilter;
    private ReactiveRedisTemplate<String, String> redisTemplate;
    private ReactiveValueOperations<String, String> valueOperations;
    private GatewayFilterChain filterChain;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() throws Exception {
        redisTemplate = mock(ReactiveRedisTemplate.class);
        valueOperations = mock(ReactiveValueOperations.class);
        filterChain = mock(GatewayFilterChain.class);
        
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(filterChain.filter(any())).thenReturn(Mono.empty());
        
        globalRateLimitFilter = new GlobalRateLimitFilter(redisTemplate);
        
        // Disable the filter for tests that don't need Redis
        Field enabledField = GlobalRateLimitFilter.class.getDeclaredField("enabled");
        enabledField.setAccessible(true);
        enabledField.setBoolean(globalRateLimitFilter, false);
    }

    @Test
    @DisplayName("Should skip rate limiting when disabled")
    void filter_shouldSkipWhenDisabled() {
        // Given
        MockServerHttpRequest request = Objects.requireNonNull(
                MockServerHttpRequest.get("/api/v1/movies").build(),
                "Request must not be null");
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        // When
        Mono<Void> result = globalRateLimitFilter.filter(exchange, filterChain);

        // Then
        StepVerifier.create(result)
                .verifyComplete();
        
        verify(redisTemplate, never()).opsForValue();
    }

    @Test
    @DisplayName("Should skip rate limiting for actuator endpoints")
    void filter_shouldSkipActuatorEndpoints() throws Exception {
        // Given
        Field enabledField = GlobalRateLimitFilter.class.getDeclaredField("enabled");
        enabledField.setAccessible(true);
        enabledField.setBoolean(globalRateLimitFilter, true);
        
        MockServerHttpRequest request = Objects.requireNonNull(
                MockServerHttpRequest.get("/actuator/health")
                        .remoteAddress(new InetSocketAddress("192.168.1.1", 8080))
                        .build(),
                "Request must not be null");
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        // When
        Mono<Void> result = globalRateLimitFilter.filter(exchange, filterChain);

        // Then
        StepVerifier.create(result)
                .verifyComplete();
        
        verify(redisTemplate, never()).opsForValue();
    }

    @Test
    @DisplayName("Should allow request when under rate limit")
    void filter_shouldAllowRequestUnderLimit() throws Exception {
        // Given
        Field enabledField = GlobalRateLimitFilter.class.getDeclaredField("enabled");
        enabledField.setAccessible(true);
        enabledField.setBoolean(globalRateLimitFilter, true);
        
        MockServerHttpRequest request = Objects.requireNonNull(
                MockServerHttpRequest.get("/api/v1/movies")
                        .remoteAddress(new InetSocketAddress("192.168.1.1", 8080))
                        .build(),
                "Request must not be null");
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        when(valueOperations.increment(anyString())).thenReturn(Mono.just(50L));

        // When
        Mono<Void> result = globalRateLimitFilter.filter(exchange, filterChain);

        // Then
        StepVerifier.create(result)
                .verifyComplete();
        
        String limitHeader = Objects.requireNonNull(
                exchange.getResponse().getHeaders().getFirst("X-RateLimit-Limit"));
        String remainingHeader = Objects.requireNonNull(
                exchange.getResponse().getHeaders().getFirst("X-RateLimit-Remaining"));
        
        assertThat(limitHeader).isEqualTo("100");
        assertThat(remainingHeader).isEqualTo("50");
        assertThat(exchange.getResponse().getStatusCode()).isNull();
    }

    @Test
    @DisplayName("Should reject request when rate limit exceeded")
    void filter_shouldRejectWhenLimitExceeded() throws Exception {
        // Given
        Field enabledField = GlobalRateLimitFilter.class.getDeclaredField("enabled");
        enabledField.setAccessible(true);
        enabledField.setBoolean(globalRateLimitFilter, true);
        
        MockServerHttpRequest request = Objects.requireNonNull(
                MockServerHttpRequest.get("/api/v1/movies")
                        .remoteAddress(new InetSocketAddress("192.168.1.1", 8080))
                        .build(),
                "Request must not be null");
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        when(valueOperations.increment(anyString())).thenReturn(Mono.just(101L));

        // When
        Mono<Void> result = globalRateLimitFilter.filter(exchange, filterChain);

        // Then
        StepVerifier.create(result)
                .verifyComplete();
        
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        
        String limitHeader = Objects.requireNonNull(
                exchange.getResponse().getHeaders().getFirst("X-RateLimit-Limit"));
        String remainingHeader = Objects.requireNonNull(
                exchange.getResponse().getHeaders().getFirst("X-RateLimit-Remaining"));
        String resetHeader = Objects.requireNonNull(
                exchange.getResponse().getHeaders().getFirst("X-RateLimit-Reset"));
        
        assertThat(limitHeader).isEqualTo("100");
        assertThat(remainingHeader).isEqualTo("0");
        assertThat(resetHeader).isEqualTo("1");
        
        verify(filterChain, never()).filter(any());
    }

    @Test
    @DisplayName("Should handle Redis errors gracefully")
    void filter_shouldHandleRedisErrors() throws Exception {
        // Given
        Field enabledField = GlobalRateLimitFilter.class.getDeclaredField("enabled");
        enabledField.setAccessible(true);
        enabledField.setBoolean(globalRateLimitFilter, true);
        
        MockServerHttpRequest request = Objects.requireNonNull(
                MockServerHttpRequest.get("/api/v1/movies")
                        .remoteAddress(new InetSocketAddress("192.168.1.1", 8080))
                        .build(),
                "Request must not be null");
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        when(valueOperations.increment(anyString()))
                .thenReturn(Mono.error(new RuntimeException("Redis connection failed")));

        // When
        Mono<Void> result = globalRateLimitFilter.filter(exchange, filterChain);

        // Then - Should allow request when Redis fails (fail open)
        StepVerifier.create(result)
                .verifyComplete();
        
        verify(filterChain).filter(any());
    }

    @Test
    @DisplayName("Should extract client IP from X-Forwarded-For header")
    void filter_shouldExtractIpFromForwardedFor() throws Exception {
        // Given
        Field enabledField = GlobalRateLimitFilter.class.getDeclaredField("enabled");
        enabledField.setAccessible(true);
        enabledField.setBoolean(globalRateLimitFilter, true);
        
        MockServerHttpRequest request = Objects.requireNonNull(
                MockServerHttpRequest.get("/api/v1/movies")
                        .header("X-Forwarded-For", "10.0.0.1, 192.168.1.1")
                        .build(),
                "Request must not be null");
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        when(valueOperations.increment(anyString())).thenReturn(Mono.just(1L));

        // When
        Mono<Void> result = globalRateLimitFilter.filter(exchange, filterChain);

        // Then
        StepVerifier.create(result)
                .verifyComplete();
        
        // Verify Redis key was created with first IP from X-Forwarded-For
        verify(valueOperations).increment("global-rate-limit:10.0.0.1");
    }

    @Test
    @DisplayName("Should have correct filter order")
    void getOrder_shouldReturnCorrectOrder() {
        // When
        int order = globalRateLimitFilter.getOrder();

        // Then
        assertThat(order).isEqualTo(org.springframework.core.Ordered.HIGHEST_PRECEDENCE + 2);
    }
}

