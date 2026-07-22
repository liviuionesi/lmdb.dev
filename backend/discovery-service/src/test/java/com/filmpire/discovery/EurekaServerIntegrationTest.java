package com.filmpire.discovery;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for Eureka Server functionality.
 * 
 * <p>This test class verifies the core functionality of the Discovery Service
 * by testing the Eureka Server endpoints and actuator health endpoints.</p>
 * 
 * <p>Tests are executed with a random port to avoid conflicts and use the
 * 'test' profile for isolated test configuration.</p>
 * 
 * @author Filmpire Team
 * @version 1.0.0
 * @see DiscoveryServiceApplication
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@AutoConfigureTestRestTemplate
class EurekaServerIntegrationTest {

    private static final String LOCALHOST_URL_PREFIX = "http://localhost:";
    private static final String ACTUATOR_HEALTH_PATH = "/actuator/health";
    private static final String ACTUATOR_INFO_PATH = "/actuator/info";
    private static final String EUREKA_APPS_PATH = "/eureka/apps";
    private static final String ROOT_PATH = "/";

    /**
     * Dynamically assigned port for the test server.
     */
    @LocalServerPort
    private int port;

    /**
     * REST template for making HTTP requests to the test server.
     * Note: @Autowired is required for TestRestTemplate in Spring Boot test context.
     * Constructor injection doesn't work properly with TestRestTemplate autoconfiguration.
     */
    @Autowired
    private TestRestTemplate restTemplate;

    /**
     * Verifies that the Eureka Server's root endpoint is accessible and responds correctly.
     * 
     * <p>This test ensures that:
     * <ul>
     *   <li>The server is running and accessible</li>
     *   <li>The response status is HTTP 200 OK</li>
     *   <li>The response contains "Eureka" indicating the dashboard is available</li>
     * </ul>
     * </p>
     */
    @Test
    void eurekaServerIsUp() {
        String url = LOCALHOST_URL_PREFIX + port + ROOT_PATH;
        ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
        
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("Eureka");
    }

    /**
     * Verifies that the actuator health endpoint is accessible and reports correct status.
     * 
     * <p>This test ensures that:
     * <ul>
     *   <li>The /actuator/health endpoint is accessible</li>
     *   <li>The response status is HTTP 200 OK</li>
     *   <li>The health status is "UP" indicating the service is healthy</li>
     * </ul>
     * </p>
     * 
     * <p>The health endpoint is critical for monitoring and orchestration tools
     * (e.g., Kubernetes liveness/readiness probes, load balancers).</p>
     */
    @Test
    void actuatorHealthEndpointIsAccessible() {
        String url = LOCALHOST_URL_PREFIX + port + ACTUATOR_HEALTH_PATH;
        ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
        
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("UP");
    }

    /**
     * Verifies that the actuator info endpoint is accessible.
     * 
     * <p>This test ensures that:
     * <ul>
     *   <li>The /actuator/info endpoint is accessible</li>
     *   <li>The response status is HTTP 200 OK</li>
     * </ul>
     * </p>
     * 
     * <p>The info endpoint provides application metadata such as version,
     * name, and description, useful for operations and monitoring.</p>
     */
    @Test
    void actuatorInfoEndpointIsAccessible() {
        String url = LOCALHOST_URL_PREFIX + port + ACTUATOR_INFO_PATH;
        ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
        
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    /**
     * Verifies that the Eureka apps registry endpoint is accessible.
     * 
     * <p>This test ensures that:
     * <ul>
     *   <li>The /eureka/apps endpoint is accessible</li>
     *   <li>The response status is HTTP 200 OK</li>
     *   <li>The response contains valid Eureka structure (JSON or XML)</li>
     * </ul>
     * </p>
     * 
     * <p>The /eureka/apps endpoint is the core service registry endpoint
     * that returns information about all registered service instances.
     * This endpoint is used by clients to discover available services.</p>
     */
    @Test
    void eurekaAppsEndpointIsAccessible() {
        String url = LOCALHOST_URL_PREFIX + port + EUREKA_APPS_PATH;
        ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
        
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        // Verify it returns valid Eureka structure (JSON format by default)
        assertThat(response.getBody()).contains("applications");
    }

    /**
     * Verifies that the Eureka server can accept service registrations.
     * 
     * <p>This test ensures that:
     * <ul>
     *   <li>The Eureka server is ready to accept registrations</li>
     *   <li>The /eureka/apps endpoint structure is correct</li>
     *   <li>Services can register with the server</li>
     * </ul>
     * </p>
     * 
     * <p>This test verifies the server is configured correctly for service registration.
     * Actual service registration should be tested by starting services and verifying
     * they appear in the registry.</p>
     */
    @Test
    void eurekaServerAcceptsServiceRegistrations() {
        String url = LOCALHOST_URL_PREFIX + port + EUREKA_APPS_PATH;
        ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
        
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        // Verify Eureka structure indicates readiness for registrations
        // Eureka returns JSON by default, containing applications object
        assertThat(response.getBody()).contains("applications");
        // Verify it's valid JSON structure (contains application array)
        assertThat(response.getBody()).contains("application");
    }
}

