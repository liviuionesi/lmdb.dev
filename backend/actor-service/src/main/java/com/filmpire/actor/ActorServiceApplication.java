package com.filmpire.actor;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.cache.annotation.EnableCaching;

/**
 * Actor Service Application. Manages actor/cast information, biographies, and filmographies, and
 * serves the TMDB v3 person facade ({@code GET /person/{id}}) — see ARCHITECTURE.md §3.6 and §5.1.
 * Eureka client is auto-configured when eureka-client dependency is present; caching backs the
 * facade's Redis read-through layer. {@code @ConfigurationPropertiesScan} enables record-based
 * {@link com.filmpire.actor.client.TmdbProperties} binding (Spring Boot 4).
 */
@SpringBootApplication
@EnableCaching
@ConfigurationPropertiesScan
public class ActorServiceApplication {

  /**
   * Application entry point for actor-service.
   *
   * @param args command-line arguments passed to the Spring Boot application
   */
  public static void main(String[] args) {
    // Launch the Spring Boot application context and initialize all configured beans.
    SpringApplication.run(ActorServiceApplication.class, args);
  }
}
