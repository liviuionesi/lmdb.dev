package com.filmpire.gateway.controller;

import com.filmpire.shared.dto.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Fallback controller for circuit breaker.
 * Provides fallback responses when downstream services are unavailable.
 *
 * @author Filmpire Development Team
 * @version 1.0.0
 */
@Slf4j
@RestController
@RequestMapping("/fallback")
public class FallbackController {

    /**
     * Fallback for Movie Service
     *
     * @return fallback response
     */
    @GetMapping("/movies")
    @PostMapping("/movies")
    public ResponseEntity<ApiResponse<Void>> movieServiceFallback() {
        log.warn("Movie Service is currently unavailable - Circuit breaker activated");
        return ResponseEntity
                .status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ApiResponse.error(
                        "Movie Service is temporarily unavailable. Please try again later.",
                        HttpStatus.SERVICE_UNAVAILABLE.value()
                ));
    }

    /**
     * Fallback for User Service
     *
     * @return fallback response
     */
    @GetMapping("/users")
    @PostMapping("/users")
    public ResponseEntity<ApiResponse<Void>> userServiceFallback() {
        log.warn("User Service is currently unavailable - Circuit breaker activated");
        return ResponseEntity
                .status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ApiResponse.error(
                        "User Service is temporarily unavailable. Please try again later.",
                        HttpStatus.SERVICE_UNAVAILABLE.value()
                ));
    }

    /**
     * Fallback for Authentication Service
     *
     * @return fallback response
     */
    @GetMapping("/auth")
    @PostMapping("/auth")
    public ResponseEntity<ApiResponse<Void>> authServiceFallback() {
        log.warn("Authentication Service is currently unavailable - Circuit breaker activated");
        return ResponseEntity
                .status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ApiResponse.error(
                        "Authentication Service is temporarily unavailable. Please try again later.",
                        HttpStatus.SERVICE_UNAVAILABLE.value()
                ));
    }

    /**
     * Fallback for Actor Service
     *
     * @return fallback response
     */
    @GetMapping("/actors")
    @PostMapping("/actors")
    public ResponseEntity<ApiResponse<Void>> actorServiceFallback() {
        log.warn("Actor Service is currently unavailable - Circuit breaker activated");
        return ResponseEntity
                .status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ApiResponse.error(
                        "Actor Service is temporarily unavailable. Please try again later.",
                        HttpStatus.SERVICE_UNAVAILABLE.value()
                ));
    }

    /**
     * Fallback for AI Service
     *
     * @return fallback response
     */
    @GetMapping("/ai")
    @PostMapping("/ai")
    public ResponseEntity<ApiResponse<Void>> aiServiceFallback() {
        log.warn("AI Service is currently unavailable - Circuit breaker activated");
        return ResponseEntity
                .status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ApiResponse.error(
                        "AI Service is temporarily unavailable. Please try again later.",
                        HttpStatus.SERVICE_UNAVAILABLE.value()
                ));
    }

    /**
     * Fallback for Media Service
     *
     * @return fallback response
     */
    @GetMapping("/media")
    @PostMapping("/media")
    public ResponseEntity<ApiResponse<Void>> mediaServiceFallback() {
        log.warn("Media Service is currently unavailable - Circuit breaker activated");
        return ResponseEntity
                .status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ApiResponse.error(
                        "Media Service is temporarily unavailable. Please try again later.",
                        HttpStatus.SERVICE_UNAVAILABLE.value()
                ));
    }
}

