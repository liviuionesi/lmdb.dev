package dev.lmdb.gateway.controller;

import dev.lmdb.shared.dto.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

/**
 * Fallback controller for circuit breaker. Provides fallback responses when downstream services are
 * unavailable.
 *
 * @author LMDB Development Team
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
  @RequestMapping(
      path = "/movies",
      method = {
        RequestMethod.GET,
        RequestMethod.POST,
        RequestMethod.PUT,
        RequestMethod.DELETE,
        RequestMethod.PATCH
      })
  public ResponseEntity<ApiResponse<Void>> movieServiceFallback() {
    log.warn("Movie Service is currently unavailable - Circuit breaker activated");
    return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
        .body(
            ApiResponse.error(
                "Movie Service is temporarily unavailable. Please try again later.",
                HttpStatus.SERVICE_UNAVAILABLE.value()));
  }

  /**
   * Fallback for User Service
   *
   * @return fallback response
   */
  @RequestMapping(
      path = "/users",
      method = {
        RequestMethod.GET,
        RequestMethod.POST,
        RequestMethod.PUT,
        RequestMethod.DELETE,
        RequestMethod.PATCH
      })
  public ResponseEntity<ApiResponse<Void>> userServiceFallback() {
    log.warn("User Service is currently unavailable - Circuit breaker activated");
    return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
        .body(
            ApiResponse.error(
                "User Service is temporarily unavailable. Please try again later.",
                HttpStatus.SERVICE_UNAVAILABLE.value()));
  }

  /**
   * Fallback for Authentication Service
   *
   * @return fallback response
   */
  @RequestMapping(
      path = "/auth",
      method = {
        RequestMethod.GET,
        RequestMethod.POST,
        RequestMethod.PUT,
        RequestMethod.DELETE,
        RequestMethod.PATCH
      })
  public ResponseEntity<ApiResponse<Void>> authServiceFallback() {
    log.warn("Authentication Service is currently unavailable - Circuit breaker activated");
    return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
        .body(
            ApiResponse.error(
                "Authentication Service is temporarily unavailable. Please try again later.",
                HttpStatus.SERVICE_UNAVAILABLE.value()));
  }

  /**
   * Fallback for Actor Service
   *
   * @return fallback response
   */
  @RequestMapping(
      path = "/actors",
      method = {
        RequestMethod.GET,
        RequestMethod.POST,
        RequestMethod.PUT,
        RequestMethod.DELETE,
        RequestMethod.PATCH
      })
  public ResponseEntity<ApiResponse<Void>> actorServiceFallback() {
    log.warn("Actor Service is currently unavailable - Circuit breaker activated");
    return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
        .body(
            ApiResponse.error(
                "Actor Service is temporarily unavailable. Please try again later.",
                HttpStatus.SERVICE_UNAVAILABLE.value()));
  }

  /**
   * Fallback for AI Service
   *
   * @return fallback response
   */
  @RequestMapping(
      path = "/ai",
      method = {
        RequestMethod.GET,
        RequestMethod.POST,
        RequestMethod.PUT,
        RequestMethod.DELETE,
        RequestMethod.PATCH
      })
  public ResponseEntity<ApiResponse<Void>> aiServiceFallback() {
    log.warn("AI Service is currently unavailable - Circuit breaker activated");
    return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
        .body(
            ApiResponse.error(
                "AI Service is temporarily unavailable. Please try again later.",
                HttpStatus.SERVICE_UNAVAILABLE.value()));
  }

  /**
   * Fallback for Media Service
   *
   * @return fallback response
   */
  @RequestMapping(
      path = "/media",
      method = {
        RequestMethod.GET,
        RequestMethod.POST,
        RequestMethod.PUT,
        RequestMethod.DELETE,
        RequestMethod.PATCH
      })
  public ResponseEntity<ApiResponse<Void>> mediaServiceFallback() {
    log.warn("Media Service is currently unavailable - Circuit breaker activated");
    return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
        .body(
            ApiResponse.error(
                "Media Service is temporarily unavailable. Please try again later.",
                HttpStatus.SERVICE_UNAVAILABLE.value()));
  }
}
