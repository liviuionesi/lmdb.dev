package com.filmpire.gateway.filter;

import com.filmpire.gateway.util.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Unit tests for JwtAuthenticationFilter.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("JwtAuthenticationFilter Tests")
class JwtAuthenticationFilterTest {

    @Mock
    private JwtUtil jwtUtil;

    @Mock
    private WebFilterChain filterChain;

    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @BeforeEach
    void setUp() {
        jwtAuthenticationFilter = new JwtAuthenticationFilter(jwtUtil);
    }

    @Test
    @DisplayName("Should allow public path without authentication")
    void filter_shouldAllowPublicPath() {
        // Given
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/v1/auth/login")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        
        when(filterChain.filter(any())).thenReturn(Mono.empty());

        // When
        Mono<Void> result = jwtAuthenticationFilter.filter(exchange, filterChain);

        // Then
        StepVerifier.create(result)
                .verifyComplete();
    }

    @Test
    @DisplayName("Should allow request without Authorization header for public endpoints")
    void filter_shouldAllowRequestWithoutAuthHeaderForPublicEndpoint() {
        // Given
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/v1/movies/123")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        
        when(filterChain.filter(any())).thenReturn(Mono.empty());

        // When
        Mono<Void> result = jwtAuthenticationFilter.filter(exchange, filterChain);

        // Then
        StepVerifier.create(result)
                .verifyComplete();
    }

    @Test
    @DisplayName("Should reject invalid JWT token")
    void filter_shouldRejectInvalidToken() {
        // Given
        String invalidToken = "invalid.jwt.token";
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/v1/users/profile")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + invalidToken)
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        when(jwtUtil.extractTokenFromHeader(anyString())).thenReturn(invalidToken);
        when(jwtUtil.validateToken(invalidToken)).thenReturn(false);

        // When
        Mono<Void> result = jwtAuthenticationFilter.filter(exchange, filterChain);

        // Then
        StepVerifier.create(result)
                .verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("Should process valid JWT token")
    void filter_shouldProcessValidToken() {
        // Given
        String validToken = "valid.jwt.token";
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/v1/users/profile")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + validToken)
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        when(jwtUtil.extractTokenFromHeader(anyString())).thenReturn(validToken);
        when(jwtUtil.validateToken(validToken)).thenReturn(true);
        when(jwtUtil.extractUsername(validToken)).thenReturn("testuser");
        when(jwtUtil.extractUserId(validToken)).thenReturn("user123");
        when(jwtUtil.extractRoles(validToken)).thenReturn(Arrays.asList("USER"));
        when(filterChain.filter(any())).thenReturn(Mono.empty());

        // When
        Mono<Void> result = jwtAuthenticationFilter.filter(exchange, filterChain);

        // Then
        StepVerifier.create(result)
                .verifyComplete();
    }

    @Test
    @DisplayName("Should continue filter chain for actuator endpoints")
    void filter_shouldAllowActuatorEndpoints() {
        // Given
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/actuator/health")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        
        when(filterChain.filter(any())).thenReturn(Mono.empty());

        // When
        Mono<Void> result = jwtAuthenticationFilter.filter(exchange, filterChain);

        // Then
        StepVerifier.create(result)
                .verifyComplete();
    }
}

