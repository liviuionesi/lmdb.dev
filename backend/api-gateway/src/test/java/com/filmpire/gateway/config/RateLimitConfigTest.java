package com.filmpire.gateway.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for RateLimitConfig.
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

    @Test
    @DisplayName("Should create IP-based KeyResolver bean")
    void ipKeyResolver_shouldBeCreated() {
        assertThat(ipKeyResolver).isNotNull();
    }

    @Test
    @DisplayName("Should create user-based KeyResolver bean")
    void userKeyResolver_shouldBeCreated() {
        assertThat(userKeyResolver).isNotNull();
    }

    @Test
    @DisplayName("Should create path-based KeyResolver bean")
    void pathKeyResolver_shouldBeCreated() {
        assertThat(pathKeyResolver).isNotNull();
    }
}

