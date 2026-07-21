package com.filmpire.gateway;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Smoke test that boots the full API Gateway Spring context via {@code @SpringBootTest}.
 * <p>
 * Runs under the "test" profile (application-test.yml), which excludes Redis
 * auto-configuration and disables Eureka, so the gateway must be able to start with no
 * external infrastructure at all. Redis-conditional beans such as GlobalRateLimitFilter
 * are therefore intentionally absent from this context.
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("API Gateway Application Tests")
class ApiGatewayApplicationTests {

    /**
     * The intentionally empty body is the point: the assertion is the context bootstrap
     * itself. Route definitions, filter beans and property binding are all evaluated at
     * startup, so any gateway misconfiguration fails here before it can hide behind
     * more specific unit tests.
     */
    @Test
    @DisplayName("Should load application context successfully")
    void contextLoads() {
        // This test verifies that the Spring application context loads successfully
        // If the context fails to load, this test will fail
    }
}

