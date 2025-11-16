package com.filmpire.gateway.filter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for IpFilterGlobalFilter.
 */
@DisplayName("IpFilterGlobalFilter Tests")
class IpFilterGlobalFilterTest {

    private IpFilterGlobalFilter ipFilterGlobalFilter;
    private GatewayFilterChain filterChain;

    @BeforeEach
    void setUp() {
        ipFilterGlobalFilter = new IpFilterGlobalFilter();
        filterChain = mock(GatewayFilterChain.class);
        when(filterChain.filter(any())).thenReturn(Mono.empty());
    }

    @Test
    @DisplayName("Should allow non-blacklisted IP")
    void filter_shouldAllowNonBlacklistedIp() {
        // Given
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/v1/movies")
                .remoteAddress(new java.net.InetSocketAddress("192.168.1.1", 8080))
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        // When
        Mono<Void> result = ipFilterGlobalFilter.filter(exchange, filterChain);

        // Then
        StepVerifier.create(result)
                .verifyComplete();
    }

    @Test
    @DisplayName("Should block blacklisted IP")
    void filter_shouldBlockBlacklistedIp() {
        // Given
        String blacklistedIp = "192.168.1.100";
        ipFilterGlobalFilter.addToBlacklist(blacklistedIp);
        
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/v1/movies")
                .remoteAddress(new java.net.InetSocketAddress(blacklistedIp, 8080))
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        // When
        Mono<Void> result = ipFilterGlobalFilter.filter(exchange, filterChain);

        // Then
        StepVerifier.create(result)
                .verifyComplete();
        
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("Should manage blacklist correctly")
    void blacklist_shouldAddAndRemoveIps() {
        // When
        ipFilterGlobalFilter.addToBlacklist("10.0.0.1");
        ipFilterGlobalFilter.addToBlacklist("10.0.0.2");

        // Then
        assertThat(ipFilterGlobalFilter.getBlacklist()).containsExactlyInAnyOrder("10.0.0.1", "10.0.0.2");

        // When
        ipFilterGlobalFilter.removeFromBlacklist("10.0.0.1");

        // Then
        assertThat(ipFilterGlobalFilter.getBlacklist()).containsExactly("10.0.0.2");
    }

    @Test
    @DisplayName("Should manage whitelist correctly")
    void whitelist_shouldAddAndRemoveIps() {
        // When
        ipFilterGlobalFilter.addToWhitelist("192.168.1.1");
        ipFilterGlobalFilter.addToWhitelist("192.168.1.2");

        // Then
        assertThat(ipFilterGlobalFilter.getWhitelist()).containsExactlyInAnyOrder("192.168.1.1", "192.168.1.2");

        // When
        ipFilterGlobalFilter.removeFromWhitelist("192.168.1.1");

        // Then
        assertThat(ipFilterGlobalFilter.getWhitelist()).containsExactly("192.168.1.2");
    }

    @Test
    @DisplayName("Should enable and disable whitelist mode")
    void whitelistMode_shouldToggle() {
        // Initially disabled
        assertThat(ipFilterGlobalFilter.isWhitelistModeEnabled()).isFalse();

        // Enable
        ipFilterGlobalFilter.enableWhitelistMode();
        assertThat(ipFilterGlobalFilter.isWhitelistModeEnabled()).isTrue();

        // Disable
        ipFilterGlobalFilter.disableWhitelistMode();
        assertThat(ipFilterGlobalFilter.isWhitelistModeEnabled()).isFalse();
    }

    @Test
    @DisplayName("Should allow actuator endpoints without filtering")
    void filter_shouldSkipActuatorEndpoints() {
        // Given
        ipFilterGlobalFilter.addToBlacklist("192.168.1.1");
        
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/actuator/health")
                .remoteAddress(new java.net.InetSocketAddress("192.168.1.1", 8080))
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        // When
        Mono<Void> result = ipFilterGlobalFilter.filter(exchange, filterChain);

        // Then - Should allow despite being blacklisted
        StepVerifier.create(result)
                .verifyComplete();
        
        assertThat(exchange.getResponse().getStatusCode()).isNull();
    }
}

