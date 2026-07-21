package com.filmpire.actor;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

/**
 * Actor Service Application.
 * Manages actor/cast information, biographies, and filmographies, and serves
 * the TMDB v3 person facade ({@code GET /person/{id}}) — see
 * ARCHITECTURE.md §3.6 and §5.1.
 * Eureka client is auto-configured when eureka-client dependency is present;
 * caching backs the facade's Redis read-through layer.
 */
@SpringBootApplication
@EnableCaching
public class ActorServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ActorServiceApplication.class, args);
    }
}

