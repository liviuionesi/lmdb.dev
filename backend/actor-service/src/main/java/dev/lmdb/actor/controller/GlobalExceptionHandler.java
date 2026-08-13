package dev.lmdb.actor.controller;

import dev.lmdb.shared.dto.ApiResponse;
import dev.lmdb.shared.exception.ResourceNotFoundException;
import dev.lmdb.shared.exception.ServiceUnavailableException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * Maps exceptions on the NATIVE {@code /api/v1} endpoints to the shared {@link ApiResponse} error
 * envelope.
 *
 * <p>Scoped to the controller package so it does not intercept the facade's own
 * {@code @ExceptionHandler}s in {@code PersonFacadeController} — facade errors must stay
 * TMDB-shaped, native errors stay ApiResponse-shaped.
 */
@RestControllerAdvice(basePackages = "dev.lmdb.actor.controller")
@Order(1)
@Slf4j
public class GlobalExceptionHandler {

  /**
   * TMDB rejected the underlying fetch (e.g. unknown person id) → mirror the upstream status with a
   * native-shaped body.
   *
   * @param e upstream error captured by Spring's RestClient
   * @return error envelope with TMDB's status code
   */
  @ExceptionHandler(RestClientResponseException.class)
  public ResponseEntity<ApiResponse<Void>> handleUpstream(RestClientResponseException e) {
    // Forward HTTP status code received from upstream TMDB in an ApiResponse envelope.
    return error(
        HttpStatus.valueOf(e.getStatusCode().value()),
        "TMDB rejected the request (HTTP " + e.getStatusCode().value() + ")");
  }

  /**
   * TMDB unreachable with no cached copy → 503.
   *
   * @param e network failure
   * @return 503 error envelope
   */
  @ExceptionHandler({ResourceAccessException.class, ServiceUnavailableException.class})
  public ResponseEntity<ApiResponse<Void>> handleUnavailable(Exception e) {
    // Log connection or service failure details and return 503 Service Unavailable.
    log.error("Upstream unavailable: {}", e.getMessage());
    return error(HttpStatus.SERVICE_UNAVAILABLE, "Actor data is temporarily unavailable");
  }

  /**
   * Missing local resources → 404.
   *
   * @param e not-found error
   * @return 404 error envelope
   */
  @ExceptionHandler(ResourceNotFoundException.class)
  public ResponseEntity<ApiResponse<Void>> handleNotFound(ResourceNotFoundException e) {
    // Return 404 Not Found with the exception's detailed error message.
    return error(HttpStatus.NOT_FOUND, e.getMessage());
  }

  /**
   * A path variable or query param that won't convert to its declared type (e.g. {@code
   * /api/v1/actors/popular} before {@code /popular} had its own mapping, or any non-numeric actor
   * id) → 400, not 500. Spring raises this before the controller runs, so without this handler it
   * would fall through to {@link #handleUnexpected} and report a server fault for what is really a
   * malformed client request.
   *
   * @param e the type-conversion failure
   * @return 400 error envelope naming the offending parameter
   */
  @ExceptionHandler(MethodArgumentTypeMismatchException.class)
  public ResponseEntity<ApiResponse<Void>> handleTypeMismatch(
      MethodArgumentTypeMismatchException e) {
    // Map parameter conversion failures to a client-friendly 400 Bad Request.
    return error(
        HttpStatus.BAD_REQUEST, "Invalid value for '" + e.getName() + "': expected a number");
  }

  /**
   * Anything unanticipated → 500 with a generic message (detail logged).
   *
   * @param e unexpected error
   * @return 500 error envelope
   */
  @ExceptionHandler(Exception.class)
  public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception e) {
    // Log full stack trace for unexpected exceptions and return generic 500 Internal Server Error.
    log.error("Unhandled exception in actor-service", e);
    return error(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred");
  }

  /**
   * Builds the standard error response.
   *
   * @param status HTTP status
   * @param message client-safe description
   * @return error envelope response
   */
  private static ResponseEntity<ApiResponse<Void>> error(HttpStatus status, String message) {
    // Assemble ResponseEntity with given HTTP status and ApiResponse error body.
    return ResponseEntity.status(status).body(ApiResponse.error(message, status.value()));
  }
}
