package com.filmpire.discovery;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class DiscoveryServiceApplicationTests {

    @Test
    void contextLoads() {
        // Verifies that the Spring context loads successfully
    }

    @Test
    void mainMethodStartsApplication() {
        // Verifies that the main method can be invoked
        DiscoveryServiceApplication.main(new String[] {});
    }
}

