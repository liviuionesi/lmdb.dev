package com.filmpire.gateway;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Application context tests for API Gateway.
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("API Gateway Application Tests")
class ApiGatewayApplicationTests {

    @Test
    @DisplayName("Should load application context successfully")
    void contextLoads() {
        // This test verifies that the Spring application context loads successfully
        // If the context fails to load, this test will fail
    }
}

