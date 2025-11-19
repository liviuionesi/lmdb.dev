package com.filmpire.gateway.filter;

import com.filmpire.gateway.util.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.lang.NonNull;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.Arrays;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

/**
 * Helper methods for null-safe test utilities
 */
final class TestNullSafety {
    @NonNull
    static ServerWebExchange createExchange(@NonNull MockServerHttpRequest request) {
        return Objects.requireNonNull(
                MockServerWebExchange.from(request),
                "MockServerWebExchange must not be null");
    }
    
    @NonNull
    static ServerWebExchange requireNonNull(ServerWebExchange exchange) {
        return Objects.requireNonNull(exchange, "Exchange must not be null");
    }
    
    @NonNull
    static WebFilterChain requireNonNull(WebFilterChain chain) {
        return Objects.requireNonNull(chain, "Filter chain must not be null");
    }
    
}

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

    @ParameterizedTest
    @ValueSource(strings = {
            "/api/v1/auth/login",
            "/api/v1/movies/123",
            "/actuator/health"
    })
    @DisplayName("Should allow public paths without authentication")
    @SuppressWarnings("null")
    void filter_shouldAllowPublicPaths(String path) {
        // Given
        MockServerHttpRequest request = Objects.requireNonNull(
                MockServerHttpRequest.get(path).build(),
                "Request must not be null");
        ServerWebExchange exchange = TestNullSafety.createExchange(request);
        WebFilterChain chain = TestNullSafety.requireNonNull(filterChain);
        doReturn(Mono.empty()).when(chain).filter(any(ServerWebExchange.class));

        // When
        Mono<Void> result = jwtAuthenticationFilter.filter(exchange, chain);

        // Then
        StepVerifier.create(result)
                .verifyComplete();
    }

    @Test
    @DisplayName("Should reject invalid JWT token")
    void filter_shouldRejectInvalidToken() {
        // Given
        String invalidToken = "invalid.jwt.token";
        MockServerHttpRequest request = Objects.requireNonNull(
                MockServerHttpRequest.get("/api/v1/users/profile")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + invalidToken)
                        .build(),
                "Request must not be null");
        ServerWebExchange exchange = TestNullSafety.createExchange(request);

        when(jwtUtil.extractTokenFromHeader(anyString())).thenReturn(invalidToken);
        when(jwtUtil.validateToken(invalidToken)).thenReturn(false);

        // When
        Mono<Void> result = jwtAuthenticationFilter.filter(
                exchange,
                TestNullSafety.requireNonNull(filterChain));

        // Then
        StepVerifier.create(result)
                .verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("Should process valid JWT token")
    @SuppressWarnings("null")
    void filter_shouldProcessValidToken() {
        // Given
        String validToken = "valid.jwt.token";
        MockServerHttpRequest request = Objects.requireNonNull(
                MockServerHttpRequest.get("/api/v1/users/profile")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + validToken)
                        .build(),
                "Request must not be null");
        ServerWebExchange exchange = TestNullSafety.createExchange(request);
        WebFilterChain chain = TestNullSafety.requireNonNull(filterChain);

        when(jwtUtil.extractTokenFromHeader(anyString())).thenReturn(validToken);
        when(jwtUtil.validateToken(validToken)).thenReturn(true);
        when(jwtUtil.extractUsername(validToken)).thenReturn("testuser");
        when(jwtUtil.extractUserId(validToken)).thenReturn("user123");
        when(jwtUtil.extractRoles(validToken)).thenReturn(Arrays.asList("USER"));
        doReturn(Mono.empty()).when(chain).filter(any());

        // When
        Mono<Void> result = jwtAuthenticationFilter.filter(exchange, chain);

        // Then
        StepVerifier.create(result)
                .verifyComplete();
    }

}

