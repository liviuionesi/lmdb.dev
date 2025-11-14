package com.filmpire.movie;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * Movie Service Application.
 * Core service managing movie data, metadata, and related operations.
 * Eureka client is auto-configured when eureka-client dependency is present.
 */
@SpringBootApplication
@EnableFeignClients
@EnableCaching
public class MovieServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(MovieServiceApplication.class, args);
    }
}

