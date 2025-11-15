package com.filmpire.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.config.server.EnableConfigServer;

/**
 * Spring Cloud Config Server for centralized configuration management.
 * 
 * <p>This service provides centralized, externalized configuration for all
 * microservices in the Filmpire application. It supports multiple environments
 * (dev, prod) and service-specific configurations.</p>
 * 
 * <h2>Key Features:</h2>
 * <ul>
 *   <li>Centralized configuration management via Spring Cloud Config</li>
 *   <li>Native mode for local development (filesystem-based)</li>
 *   <li>Git mode support for production deployments</li>
 *   <li>Environment-specific profiles (dev, prod)</li>
 *   <li>Service-specific configuration files</li>
 *   <li>Integration with Eureka for service discovery</li>
 *   <li>Security via environment variables for sensitive data</li>
 *   <li>Support for configuration encryption</li>
 * </ul>
 * 
 * <h2>Configuration Structure:</h2>
 * <pre>
 * src/main/resources/config/
 * ├── application.yml              # Common configuration
 * ├── application-dev.yml          # Development environment
 * ├── application-prod.yml         # Production environment
 * ├── movie-service.yml            # Movie service specific
 * ├── user-service.yml             # User service specific
 * ├── actor-service.yml            # Actor service specific
 * ├── ai-service.yml               # AI service specific
 * ├── media-service.yml            # Media service specific
 * └── api-gateway.yml              # API Gateway specific
 * </pre>
 * 
 * <h2>Configuration Access:</h2>
 * <p>Services retrieve configuration via HTTP endpoints:</p>
 * <ul>
 *   <li>http://localhost:8888/{application}/default - Default profile</li>
 *   <li>http://localhost:8888/{application}/dev - Development profile</li>
 *   <li>http://localhost:8888/{application}/prod - Production profile</li>
 * </ul>
 * 
 * <h2>Security Considerations:</h2>
 * <ul>
 *   <li>All sensitive values use environment variables (${ENV_VAR})</li>
 *   <li>No hardcoded passwords or API keys in configuration files</li>
 *   <li>.env file is gitignored (never committed to version control)</li>
 *   <li>Supports Spring Cloud Config encryption for production</li>
 * </ul>
 * 
 * <h2>Required Environment Variables:</h2>
 * <ul>
 *   <li>POSTGRES_PASSWORD - PostgreSQL database password</li>
 *   <li>MONGODB_URI - MongoDB connection string with credentials</li>
 *   <li>JWT_SECRET - JWT signing secret (minimum 512 bits)</li>
 *   <li>MINIO_ACCESS_KEY / MINIO_SECRET_KEY - Object storage credentials</li>
 * </ul>
 * 
 * <p>The {@code @EnableConfigServer} annotation activates Spring Cloud Config Server
 * functionality, allowing this service to serve configuration to other microservices.</p>
 * 
 * <p>Eureka client is auto-configured when eureka-client dependency is present,
 * enabling automatic service registration and discovery.</p>
 * 
 * @author Filmpire Team
 * @version 1.0.0
 * @since 1.0.0
 * @see org.springframework.cloud.config.server.EnableConfigServer
 * @see org.springframework.boot.autoconfigure.SpringBootApplication
 */
@SpringBootApplication
@EnableConfigServer
public class ConfigServiceApplication {

    /**
     * Main entry point for the Config Service application.
     * 
     * <p>Initializes and starts the Spring Boot application context,
     * enabling the Config Server to serve configuration files to
     * registered microservices.</p>
     * 
     * <h3>Startup Sequence:</h3>
     * <ol>
     *   <li>Load application.yml configuration</li>
     *   <li>Initialize Config Server (native or git mode)</li>
     *   <li>Load configuration files from search locations</li>
     *   <li>Register with Eureka Discovery Server</li>
     *   <li>Start embedded web server on port 8888</li>
     *   <li>Expose configuration endpoints</li>
     * </ol>
     * 
     * <p>The server will be available at: http://localhost:8888</p>
     * 
     * @param args command-line arguments (not used)
     * @throws IllegalStateException if the application fails to start
     */
    public static void main(String[] args) {
        SpringApplication.run(ConfigServiceApplication.class, args);
    }
}

