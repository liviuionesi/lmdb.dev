package com.filmpire.discovery;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.netflix.eureka.server.EnableEurekaServer;

/**
 * Discovery Service Application - Eureka Server
 * 
 * <p>This service acts as the service registry for the Filmpire microservices architecture.
 * It enables service discovery and registration for all microservices in the system.</p>
 * 
 * <p>Key features:
 * <ul>
 *   <li>Service registration and discovery</li>
 *   <li>Health monitoring</li>
 *   <li>Load balancing support</li>
 *   <li>Web-based dashboard</li>
 * </ul>
 * </p>
 * 
 * @author Filmpire Team
 * @version 1.0.0
 */
@SpringBootApplication
@EnableEurekaServer
public class DiscoveryServiceApplication {

    /**
     * Main entry point for the Discovery Service application.
     * 
     * @param args command line arguments
     */
    public static void main(String[] args) {
        SpringApplication.run(DiscoveryServiceApplication.class, args);
    }
}

