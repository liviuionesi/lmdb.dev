package com.filmpire.config.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Health check and status endpoint for Config Service.
 * 
 * <p>Provides a simple REST endpoint to verify that the Config Service is
 * operational and properly registered with the Eureka service registry.</p>
 * 
 * <p>This endpoint is useful for:</p>
 * <ul>
 *   <li>Service discovery verification</li>
 *   <li>Manual health checks during development</li>
 *   <li>Integration testing with Eureka</li>
 *   <li>Monitoring service availability</li>
 * </ul>
 * 
 * <p><strong>Note:</strong> For production monitoring, use the Spring Boot Actuator
 * health endpoint at {@code /actuator/health} instead, which provides more
 * comprehensive health information.</p>
 * 
 * @author Filmpire Team
 * @version 1.0.0
 * @since 1.0.0
 * @see org.springframework.boot.actuate.health.HealthEndpoint
 */
@RestController
@RequestMapping("/hello")
public class HelloController {

    /**
     * Returns a simple status message indicating the service is operational.
     * 
     * <p>This endpoint responds with a JSON object containing:</p>
     * <ul>
     *   <li><strong>service:</strong> The service identifier (config-service)</li>
     *   <li><strong>message:</strong> A friendly greeting message</li>
     *   <li><strong>status:</strong> Service health status (UP)</li>
     * </ul>
     * 
     * <h3>Example Response:</h3>
     * <pre>
     * {
     *   "service": "config-service",
     *   "message": "Hello from Config Service!",
     *   "status": "UP"
     * }
     * </pre>
     * 
     * <h3>Usage:</h3>
     * <pre>
     * curl http://localhost:8888/hello
     * </pre>
     * 
     * @return ResponseEntity containing service status information with HTTP 200 OK
     */
    @GetMapping
    public ResponseEntity<Map<String, String>> hello() {
        return ResponseEntity.ok(Map.of(
            "service", "config-service",
            "message", "Hello from Config Service!",
            "status", "UP"
        ));
    }
}

