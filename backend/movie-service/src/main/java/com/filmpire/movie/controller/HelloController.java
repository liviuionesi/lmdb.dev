package com.filmpire.movie.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Hello World controller for Movie Service.
 * Provides a simple endpoint to verify the service is running and registered with Eureka.
 */
@RestController
@RequestMapping("/hello")
public class HelloController {

    @GetMapping
    public ResponseEntity<Map<String, String>> hello() {
        return ResponseEntity.ok(Map.of(
            "service", "movie-service",
            "message", "Hello from Movie Service!",
            "status", "UP"
        ));
    }
}

