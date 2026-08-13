package dev.lmdb.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.config.server.EnableConfigServer;

/**
 * Spring Cloud Config Server for centralized configuration management.
 *
 * <p>This service provides centralized, externalized configuration for all microservices in the
 * LMDB application. It supports multiple environments (dev, prod) and service-specific
 * configurations.
 *
 * <h2>Key Features:</h2>
 *
 * <ul>
 *   <li>Centralized configuration management via Spring Cloud Config
 *   <li>Native mode for local development (filesystem-based)
 *   <li>Git mode support for production deployments
 *   <li>Environment-specific profiles (dev, prod)
 *   <li>Service-specific configuration files
 *   <li>Integration with Eureka for service discovery
 *   <li>Security via environment variables for sensitive data
 *   <li>Support for configuration encryption
 * </ul>
 *
 * <h2>Configuration Structure:</h2>
 *
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
 *
 * <p>Services retrieve configuration via HTTP endpoints:
 *
 * <ul>
 *   <li>http://localhost:8888/{application}/default - Default profile
 *   <li>http://localhost:8888/{application}/dev - Development profile
 *   <li>http://localhost:8888/{application}/prod - Production profile
 * </ul>
 *
 * <h2>Security Considerations:</h2>
 *
 * <ul>
 *   <li>All sensitive values use environment variables (${ENV_VAR})
 *   <li>No hardcoded passwords or API keys in configuration files
 *   <li>.env file is gitignored (never committed to version control)
 *   <li>Supports Spring Cloud Config encryption for production
 * </ul>
 *
 * <h2>Required Environment Variables:</h2>
 *
 * <ul>
 *   <li>POSTGRES_PASSWORD - PostgreSQL database password
 *   <li>MONGODB_URI - MongoDB connection string with credentials
 *   <li>JWT_SECRET - JWT signing secret (minimum 512 bits)
 *   <li>MINIO_ACCESS_KEY / MINIO_SECRET_KEY - Object storage credentials
 * </ul>
 *
 * <p>The {@code @EnableConfigServer} annotation activates Spring Cloud Config Server functionality,
 * allowing this service to serve configuration to other microservices.
 *
 * <p>Eureka client is auto-configured when eureka-client dependency is present, enabling automatic
 * service registration and discovery.
 *
 * @author LMDB Team
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
   * <p>Initializes and starts the Spring Boot application context, enabling the Config Server to
   * serve configuration files to registered microservices.
   *
   * <h3>Startup Sequence:</h3>
   *
   * <ol>
   *   <li>Load application.yml configuration
   *   <li>Initialize Config Server (native or git mode)
   *   <li>Load configuration files from search locations
   *   <li>Register with Eureka Discovery Server
   *   <li>Start embedded web server on port 8888
   *   <li>Expose configuration endpoints
   * </ol>
   *
   * <p>The server will be available at: http://localhost:8888
   *
   * @param args command-line arguments (not used)
   * @throws IllegalStateException if the application fails to start
   */
  public static void main(String[] args) {
    SpringApplication.run(ConfigServiceApplication.class, args);
  }
}
