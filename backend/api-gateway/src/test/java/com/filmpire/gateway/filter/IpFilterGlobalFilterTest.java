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
 * Unit tests for {@link IpFilterGlobalFilter}, the gateway's IP allow/deny
 * filter.
 *
 * <p>The downstream {@link GatewayFilterChain} is mocked to complete empty, so
 * a completed chain means "request admitted" and a mutated 403 response means
 * "request blocked". Covers both the request-filtering decisions and the
 * blacklist/whitelist management API that drives them.</p>
 */
@DisplayName("IpFilterGlobalFilter Tests")
class IpFilterGlobalFilterTest {

    private IpFilterGlobalFilter ipFilterGlobalFilter;
    private GatewayFilterChain filterChain;

    /** Fresh filter with an empty blacklist/whitelist and a pass-through chain. */
    @BeforeEach
    void setUp() {
        ipFilterGlobalFilter = new IpFilterGlobalFilter();
        filterChain = mock(GatewayFilterChain.class);
        when(filterChain.filter(any())).thenReturn(Mono.empty());
    }

    /**
     * The default-open case: an IP that is on no blacklist must be admitted,
     * so ordinary traffic flows unless explicitly denied.
     */
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

    /**
     * A request from a blacklisted IP must be stopped with 403 before reaching
     * any service — the core deny-listing behavior. Asserted on the mutated
     * response status because the filter short-circuits rather than throwing.
     */
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

    /**
     * The blacklist must behave as a set that supports add and remove, since
     * operators toggle entries at runtime; a broken remove would strand an IP
     * as permanently blocked.
     */
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

    /**
     * The whitelist mirrors the blacklist's add/remove semantics — verified
     * independently because the two lists are separate state and a copy-paste
     * bug could wire one to the other.
     */
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

    /**
     * Whitelist mode must default OFF (fail-open to blacklisting) and toggle
     * cleanly, because flipping it ON changes the filter from deny-list to
     * allow-list semantics — a security-significant switch that must be
     * deliberate, never stuck.
     */
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

    /**
     * Actuator endpoints must bypass IP filtering even for a blacklisted IP,
     * so health/readiness probes (which may originate from an
     * infrastructure address that happens to be blocked) never get cut off and
     * take the platform down. A null status confirms the filter didn't touch
     * the response.
     */
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

