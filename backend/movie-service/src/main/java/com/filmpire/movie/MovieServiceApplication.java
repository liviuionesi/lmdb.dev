package com.filmpire.movie;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * Movie Service Application.
 * 
 * <p>Provides movie discovery, search, and details functionality.</p>
 * 
 * <p>Features:</p>
 * <ul>
 *   <li>TMDB API integration for movie data</li>
 *   <li>Hybrid caching strategy (Redis + MongoDB)</li>
 *   <li>Service discovery with Eureka</li>
 *   <li>Centralized configuration with Config Server</li>
 *   <li>API documentation with OpenAPI/Swagger</li>
 * </ul>
 * 
 * @author Filmpire Team
 * @version 1.0.0
 */
@SpringBootApplication
@EnableFeignClients
@EnableCaching
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
