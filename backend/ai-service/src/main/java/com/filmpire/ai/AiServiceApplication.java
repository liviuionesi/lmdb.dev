package com.filmpire.ai;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * AI Service Application.
 * Provides AI-powered features: voice recognition, recommendations, and intelligent search.
 * Eureka client is auto-configured when eureka-client dependency is present.
 */
@SpringBootApplication
public class AiServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiServiceApplication.class, args);
    }
}

