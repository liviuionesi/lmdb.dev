package com.filmpire.gateway.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * Hello World controller for API Gateway.
 * Provides a simple endpoint to verify the service is running and registered with Eureka.
 */
@RestController
@RequestMapping("/hello")
public class HelloController {

    @GetMapping
    public Mono<ResponseEntity<Map<String, String>>> hello() {
        return Mono.just(ResponseEntity.ok(Map.of(
            "service", "api-gateway",
            "message", "Hello from API Gateway!",
            "status", "UP"
        )));
    }
}

