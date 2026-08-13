package dev.lmdb.movie;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Movie Service Application.
 *
 * <p>Provides movie discovery, search, and details functionality.
 *
 * <p>Features:
 *
 * <ul>
 *   <li>TMDB API integration for movie data
 *   <li>Hybrid caching strategy (Redis + MongoDB)
 *   <li>Service discovery with Eureka
 *   <li>Centralized configuration with Config Server
 *   <li>API documentation with OpenAPI/Swagger
 * </ul>
 *
 * @author LMDB Team
 * @version 1.0.0
 */
@SpringBootApplication
@EnableCaching
@EnableScheduling
public class MovieServiceApplication {

  /**
   * Application entry point.
   *
   * @param args Command line arguments
   */
  public static void main(String[] args) {
    SpringApplication.run(MovieServiceApplication.class, args);
  }
}
