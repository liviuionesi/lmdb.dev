package dev.lmdb.actor.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import dev.lmdb.shared.dto.ApiResponse;
import dev.lmdb.shared.exception.ResourceNotFoundException;
import dev.lmdb.shared.exception.ServiceUnavailableException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * Direct unit tests for {@link GlobalExceptionHandler}.
 *
 * <p>Exercises each exception handler method in isolation to verify status code mapping and
 * standard {@link ApiResponse} error envelope packaging without HTTP transport overhead.
 */
@DisplayName("GlobalExceptionHandler Unit Tests")
class GlobalExceptionHandlerTest {

  private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

  /**
   * Verifies that {@link GlobalExceptionHandler#handleUpstream} captures the upstream status code
   * from a {@link org.springframework.web.client.RestClientResponseException} and packages it into
   * an error envelope with the matching HTTP status.
   */
  @Test
  @DisplayName("handleUpstream: mirrors upstream HTTP status in ApiResponse envelope")
  void handleUpstreamMirrorsStatus() {
    // Given
    var ex =
        HttpClientErrorException.create(
            HttpStatus.NOT_FOUND,
            "Not Found",
            HttpHeaders.EMPTY,
            "{}".getBytes(StandardCharsets.UTF_8),
            StandardCharsets.UTF_8);

    // When
    ResponseEntity<ApiResponse<Void>> response = handler.handleUpstream(ex);

    // Then
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().isSuccess()).isFalse();
    assertThat(response.getBody().getStatusCode()).isEqualTo(404);
    assertThat(response.getBody().getMessage()).contains("TMDB rejected the request (HTTP 404)");
  }

  /**
   * Verifies that {@link GlobalExceptionHandler#handleUnavailable} maps network transport failures
   * (e.g. {@link ResourceAccessException}) to HTTP 503 Service Unavailable.
   */
  @Test
  @DisplayName("handleUnavailable: maps ResourceAccessException to 503")
  void handleUnavailableMapsResourceAccess() {
    // Given
    var ex = new ResourceAccessException("Connection timed out");

    // When
    ResponseEntity<ApiResponse<Void>> response = handler.handleUnavailable(ex);

    // Then
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().isSuccess()).isFalse();
    assertThat(response.getBody().getMessage()).isEqualTo("Actor data is temporarily unavailable");
  }

  /**
   * Verifies that {@link GlobalExceptionHandler#handleUnavailable} maps explicit {@link
   * ServiceUnavailableException} instances to HTTP 503 Service Unavailable.
   */
  @Test
  @DisplayName("handleUnavailable: maps ServiceUnavailableException to 503")
  void handleUnavailableMapsServiceUnavailable() {
    // Given
    var ex = new ServiceUnavailableException("Downstream outage");

    // When
    ResponseEntity<ApiResponse<Void>> response = handler.handleUnavailable(ex);

    // Then
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().isSuccess()).isFalse();
  }

  /**
   * Verifies that {@link GlobalExceptionHandler#handleNotFound} maps {@link
   * ResourceNotFoundException} to HTTP 404 Not Found preserving the detail message.
   */
  @Test
  @DisplayName("handleNotFound: maps ResourceNotFoundException to 404")
  void handleNotFoundMapsResourceNotFound() {
    // Given
    var ex = new ResourceNotFoundException("Actor not found: 42");

    // When
    ResponseEntity<ApiResponse<Void>> response = handler.handleNotFound(ex);

    // Then
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().getMessage()).isEqualTo("Actor not found: 42");
  }

  /**
   * Verifies that {@link GlobalExceptionHandler#handleTypeMismatch} produces HTTP 400 Bad Request
   * naming the invalid parameter.
   */
  @Test
  @DisplayName("handleTypeMismatch: maps MethodArgumentTypeMismatchException to 400")
  void handleTypeMismatchMapsBadRequest() {
    // Given
    MethodParameter param = mock(MethodParameter.class);
    MethodArgumentTypeMismatchException ex =
        new MethodArgumentTypeMismatchException(
            "abc", Long.class, "id", param, new NumberFormatException());

    // When
    ResponseEntity<ApiResponse<Void>> response = handler.handleTypeMismatch(ex);

    // Then
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().getMessage()).contains("Invalid value for 'id'");
  }

  /**
   * Verifies that {@link GlobalExceptionHandler#handleUnexpected} maps unanticipated exceptions to
   * HTTP 500 Internal Server Error with a sanitized error message.
   */
  @Test
  @DisplayName("handleUnexpected: maps unexpected exceptions to 500")
  void handleUnexpectedMapsInternalError() {
    // Given
    var ex = new NullPointerException("Simulated bug");

    // When
    ResponseEntity<ApiResponse<Void>> response = handler.handleUnexpected(ex);

    // Then
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().getMessage()).isEqualTo("An unexpected error occurred");
  }
}
