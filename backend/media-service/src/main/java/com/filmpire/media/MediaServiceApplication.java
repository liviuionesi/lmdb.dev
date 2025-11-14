package com.filmpire.media;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Media Service Application.
 * Handles media file storage, processing, and delivery (posters, trailers, images).
 * Eureka client is auto-configured when eureka-client dependency is present.
 */
@SpringBootApplication
public class MediaServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(MediaServiceApplication.class, args);
    }
}

