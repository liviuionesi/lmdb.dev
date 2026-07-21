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
 * Unit tests for {@link GlobalRateLimitFilter}, the gateway's Redis-backed
 * per-IP rate limiter.
 *
 * <p>Redis is mocked via {@link ReactiveRedisTemplate}/{@link
 * ReactiveValueOperations}, so a test controls the observed request count by
 * stubbing {@code increment()}. Reflection flips the private {@code enabled}
 * flag because the filter defaults off; each test that needs limiting turns it
 * on explicitly. Key behaviors covered: the enabled/disabled and actuator
 * bypasses, the under-limit vs over-limit decision and its response headers,
 * fail-open on Redis errors, and client-IP resolution from X-Forwarded-For.</p>
 */
@DisplayName("GlobalRateLimitFilter Tests")
class GlobalRateLimitFilterTest {

    private GlobalRateLimitFilter globalRateLimitFilter;
    private ReactiveRedisTemplate<String, String> redisTemplate;
    private ReactiveValueOperations<String, String> valueOperations;
    private GatewayFilterChain filterChain;

    /**
     * Wires mocked Redis + chain into a fresh filter and forces {@code enabled}
     * to false as the default, so tests opt into rate limiting deliberately.
     *
     * @throws Exception if the reflective field access fails
     */
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

    /**
     * When the feature flag is off, the filter must not even touch Redis —
     * verified by asserting opsForValue() is never called. This keeps the
     * gateway fully functional (and Redis-independent) when limiting is
     * turned off.
     */
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

    /**
     * Even with limiting ON, actuator endpoints must be exempt so health
     * probes are never throttled into failure. Asserted via Redis being
     * untouched for an actuator path.
     *
     * @throws Exception if the reflective enable fails
     */
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

    /**
     * A count under the limit (stubbed 50 of 100) must pass AND advertise the
     * budget via X-RateLimit-Limit/Remaining headers, so well-behaved clients
     * can self-throttle. A null status confirms the request was not rejected.
     *
     * @throws Exception if the reflective enable fails
     */
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

    /**
     * A count over the limit (stubbed 101) must yield 429, emit
     * Remaining:0 / Reset headers, and — critically — NOT forward the request
     * (verify the chain is never called), so an abusive client can't reach
     * downstream services.
     *
     * @throws Exception if the reflective enable fails
     */
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

    /**
     * FAIL-OPEN policy: if Redis errors, the filter must let the request
     * through (chain called) rather than block legitimate traffic on an
     * infrastructure hiccup. A rate limiter that fails closed would turn a
     * Redis blip into a full outage — a worse failure than briefly unmetered
     * traffic.
     *
     * @throws Exception if the reflective enable fails
     */
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

    /**
     * Behind a proxy the socket address is the proxy, so the limiter must key
     * on the FIRST hop in X-Forwarded-For (the real client) — asserted by the
     * exact Redis key "global-rate-limit:10.0.0.1". Keying on the proxy IP
     * instead would lump all users into one shared bucket.
     *
     * @throws Exception if the reflective enable fails
     */
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

    /**
     * Rate limiting must sit just after logging in the chain (HIGHEST+2) so
     * rejected requests are still logged but are dropped before the more
     * expensive auth/routing filters run. Pinning the order guards that
     * placement against accidental reordering.
     */
    @Test
    @DisplayName("Should have correct filter order")
    void getOrder_shouldReturnCorrectOrder() {
        // When
        int order = globalRateLimitFilter.getOrder();

        // Then
        assertThat(order).isEqualTo(org.springframework.core.Ordered.HIGHEST_PRECEDENCE + 2);
    }
}

