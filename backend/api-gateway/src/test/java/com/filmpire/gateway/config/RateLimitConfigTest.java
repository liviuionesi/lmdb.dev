package com.filmpire.gateway.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Context test for {@link RateLimitConfig}, which declares the three
 * {@link KeyResolver} beans Spring Cloud Gateway's RequestRateLimiter can key
 * buckets on (per-IP, per-user, per-path).
 *
 * <p>The routes reference these resolvers by bean name, so if a bean is
 * missing or misnamed the gateway fails to start. Autowiring all three by
 * their distinct field names and asserting presence catches that wiring
 * mistake at build time rather than at deploy time. There are three separate
 * resolver beans of the same type, so name-based injection also verifies they
 * are distinct and unambiguously qualified.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("RateLimitConfig Tests")
class RateLimitConfigTest {

    @Autowired
    private KeyResolver ipKeyResolver;

    @Autowired
    private KeyResolver userKeyResolver;

    @Autowired
    private KeyResolver pathKeyResolver;

    /** The per-IP resolver bean must exist — routes that rate-limit by client
     *  IP reference it by name and won't wire without it. */
    @Test
    @DisplayName("Should create IP-based KeyResolver bean")
    void ipKeyResolver_shouldBeCreated() {
        assertThat(ipKeyResolver).isNotNull();
    }

    /** The per-user resolver bean must exist for authenticated per-account
     *  limiting. */
    @Test
    @DisplayName("Should create user-based KeyResolver bean")
    void userKeyResolver_shouldBeCreated() {
        assertThat(userKeyResolver).isNotNull();
    }

    /** The per-path resolver bean must exist for endpoint-scoped limiting. */
    @Test
    @DisplayName("Should create path-based KeyResolver bean")
    void pathKeyResolver_shouldBeCreated() {
        assertThat(pathKeyResolver).isNotNull();
    }
}

