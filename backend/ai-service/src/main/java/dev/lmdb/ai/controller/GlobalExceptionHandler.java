package dev.lmdb.ai.controller;

import dev.lmdb.shared.dto.ApiResponse;
import dev.lmdb.shared.exception.ResourceNotFoundException;
import dev.lmdb.shared.exception.ServiceUnavailableException;
import dev.lmdb.shared.exception.UnauthorizedException;
import dev.lmdb.shared.exception.ValidationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * Maps ai-service's exceptions to the shared {@link ApiResponse} error envelope, mirroring
 * actor-service's and user-service's handlers.
 */
@RestControllerAdvice(basePackages = "dev.lmdb.ai.controller")
@Slf4j
public class GlobalExceptionHandler {

  /**
   * A requested conversation doesn't exist, or exists but is not owned by the requesting user (see
   * {@link dev.lmdb.ai.repository.ConversationRepository#findByIdAndUserId}) → 404.
   *
   * @param e not-found error
   * @return 404 error envelope
   */
  @ExceptionHandler(ResourceNotFoundException.class)
  public ResponseEntity<ApiResponse<Void>> handleNotFound(ResourceNotFoundException e) {
    return error(HttpStatus.NOT_FOUND, e.getMessage());
  }

  /**
   * The caller reached a user-scoped endpoint without a usable gateway-issued identity header (see
   * {@link dev.lmdb.ai.security.CallerIdentity}) → 401. Reaching this in production means the
   * request bypassed api-gateway, since the gateway rejects an unauthenticated call to {@code
   * /api/v1/ai/**} before it is ever routed here.
   *
   * @param e missing-or-unusable caller identity
   * @return 401 error envelope
   */
  @ExceptionHandler(UnauthorizedException.class)
  public ResponseEntity<ApiResponse<Void>> handleUnauthorized(UnauthorizedException e) {
    log.warn("Rejected unauthenticated request to a user-scoped endpoint");
    return error(HttpStatus.UNAUTHORIZED, e.getMessage());
  }

  /**
   * A path variable or query param that won't convert to its declared type → 400, not 500.
   *
   * @param e the type-conversion failure
   * @return 400 error envelope naming the offending parameter
   */
  @ExceptionHandler(MethodArgumentTypeMismatchException.class)
  public ResponseEntity<ApiResponse<Void>> handleTypeMismatch(
      MethodArgumentTypeMismatchException e) {
    return error(
        HttpStatus.BAD_REQUEST, "Invalid value for '" + e.getName() + "': expected a number");
  }

  /**
   * A speech-to-text upload that isn't a readable audio file → 400.
   *
   * @param e audio-format validation failure
   * @return 400 error envelope
   */
  @ExceptionHandler(ValidationException.class)
  public ResponseEntity<ApiResponse<Void>> handleValidation(ValidationException e) {
    return error(HttpStatus.BAD_REQUEST, e.getMessage());
  }

  /**
   * A local, optional dependency isn't ready — the Vosk speech-to-text model hasn't been downloaded
   * yet, mirroring how an unreachable Ollama degrades rather than crashing the request thread.
   *
   * @param e dependency-not-ready error
   * @return 503 error envelope
   */
  @ExceptionHandler(ServiceUnavailableException.class)
  public ResponseEntity<ApiResponse<Void>> handleUnavailable(ServiceUnavailableException e) {
    log.warn("Dependency unavailable: {}", e.getMessage());
    return error(HttpStatus.SERVICE_UNAVAILABLE, e.getMessage());
  }

  /**
   * Anything unanticipated, including the Ollama client being unreachable — the model server is a
   * local, optional dependency, so a 500 here (rather than crashing the request thread) is the
   * correct degrade.
   *
   * @param e unexpected error
   * @return 500 error envelope
   */
  @ExceptionHandler(Exception.class)
  public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception e) {
    log.error("Unhandled exception in ai-service", e);
    return error(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred");
  }

  /**
   * Wraps a status and message in the shared {@link ApiResponse} error envelope.
   *
   * @param status the HTTP status to respond with
   * @param message the error message to surface to the caller
   * @return the fully-built error response
   */
  private static ResponseEntity<ApiResponse<Void>> error(HttpStatus status, String message) {
    return ResponseEntity.status(status).body(ApiResponse.error(message, status.value()));
  }
}
