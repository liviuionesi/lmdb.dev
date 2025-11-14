package com.filmpire.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * API Gateway Application.
 * Entry point for the API Gateway service that routes requests to microservices.
 * Eureka client is auto-configured when eureka-client dependency is present.
 */
@SpringBootApplication
public class ApiGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(ApiGatewayApplication.class, args);
    }
}

