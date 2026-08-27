package dev.lmdb.ai.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import dev.lmdb.shared.dto.ApiResponse;
import dev.lmdb.shared.exception.ResourceNotFoundException;
import dev.lmdb.shared.exception.ServiceUnavailableException;
import dev.lmdb.shared.exception.UnauthorizedException;
import dev.lmdb.shared.exception.ValidationException;
import java.util.HashMap;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.validation.MapBindingResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

/**
 * Direct unit tests for {@link GlobalExceptionHandler}, mirroring actor-service's {@code
 * GlobalExceptionHandlerTest}. Exercises each exception handler method in isolation — with a
 * hand-built exception, no Spring context or HTTP transport — so the status/message mapping is
 * pinned down independently of whether MockMvc or a real request can actually trigger that
 * exception type (some of these, e.g. {@link MaxUploadSizeExceededException}, are awkward or
 * impossible to provoke through a MockMvc-driven integration test — see {@code
 * AiServiceIntegrationTest} for the handlers that *are* reachable that way).
 */
@DisplayName("GlobalExceptionHandler Unit Tests")
class GlobalExceptionHandlerTest {

  private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

  /**
   * Verifies that {@link GlobalExceptionHandler#handleNotFound} maps {@link
   * ResourceNotFoundException} to 404, preserving the detail message.
   */
  @Test
  @DisplayName("handleNotFound: maps ResourceNotFoundException to 404")
  void handleNotFoundMapsResourceNotFound() {
    var ex = new ResourceNotFoundException("Conversation not found: 42");

    ResponseEntity<ApiResponse<Void>> response = handler.handleNotFound(ex);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().getMessage()).isEqualTo("Conversation not found: 42");
  }

  /**
   * Verifies that {@link GlobalExceptionHandler#handleUnauthorized} maps {@link
   * UnauthorizedException} to 401.
   */
  @Test
  @DisplayName("handleUnauthorized: maps UnauthorizedException to 401")
  void handleUnauthorizedMapsUnauthorized() {
    var ex = new UnauthorizedException("Authentication required");

    ResponseEntity<ApiResponse<Void>> response = handler.handleUnauthorized(ex);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().getMessage()).isEqualTo("Authentication required");
  }

  /**
   * Verifies that {@link GlobalExceptionHandler#handleTypeMismatch} maps a path/query parameter
   * that fails to convert to its declared type to 400, naming the offending parameter.
   */
  @Test
  @DisplayName("handleTypeMismatch: maps MethodArgumentTypeMismatchException to 400")
  void handleTypeMismatchMapsBadRequest() {
    MethodParameter param = mock(MethodParameter.class);
    var ex =
        new MethodArgumentTypeMismatchException(
            "abc", Integer.class, "k", param, new NumberFormatException());

    ResponseEntity<ApiResponse<Void>> response = handler.handleTypeMismatch(ex);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().getMessage()).contains("k");
  }

  /**
   * Verifies that {@link GlobalExceptionHandler#handleValidation} maps {@link ValidationException}
   * to 400.
   */
  @Test
  @DisplayName("handleValidation: maps ValidationException to 400")
  void handleValidationMapsBadRequest() {
    var ex = new ValidationException("Unsupported audio format");

    ResponseEntity<ApiResponse<Void>> response = handler.handleValidation(ex);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().getMessage()).isEqualTo("Unsupported audio format");
  }

  /**
   * Given a {@code @Valid @RequestBody} that failed bean validation on one field, when {@link
   * GlobalExceptionHandler#handleMethodArgumentNotValid} handles it, then the response is 400 and
   * the message names the offending field — not just the bare constraint message, which alone
   * doesn't say which field failed on a multi-field DTO.
   */
  @Test
  @DisplayName("handleMethodArgumentNotValid: maps to 400 naming the offending field")
  void handleMethodArgumentNotValidNamesTheField() {
    var bindingResult = new MapBindingResult(new HashMap<>(), "chatRequestBodyDto");
    bindingResult.addError(new FieldError("chatRequestBodyDto", "message", "must not be blank"));
    MethodParameter param = mock(MethodParameter.class);
    var ex = new MethodArgumentNotValidException(param, bindingResult);

    ResponseEntity<ApiResponse<Void>> response = handler.handleMethodArgumentNotValid(ex);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().getMessage()).contains("message").contains("must not be blank");
  }

  /**
   * Verifies that {@link GlobalExceptionHandler#handleHttpMessageNotReadable} maps unparseable JSON
   * to 400, instead of the catch-all 500 it fell through to before this handler existed.
   */
  @Test
  @DisplayName("handleHttpMessageNotReadable: maps HttpMessageNotReadableException to 400")
  void handleHttpMessageNotReadableMapsBadRequest() {
    HttpInputMessage inputMessage = mock(HttpInputMessage.class);
    var ex = new HttpMessageNotReadableException("JSON parse error", inputMessage);

    ResponseEntity<ApiResponse<Void>> response = handler.handleHttpMessageNotReadable(ex);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().isSuccess()).isFalse();
  }

  /**
   * Verifies that {@link GlobalExceptionHandler#handleMissingServletRequestParameter} maps an
   * omitted required query parameter to 400, naming the missing parameter.
   */
  @Test
  @DisplayName(
      "handleMissingServletRequestParameter: maps MissingServletRequestParameterException to 400")
  void handleMissingServletRequestParameterNamesTheParameter() {
    var ex = new MissingServletRequestParameterException("query", "String");

    ResponseEntity<ApiResponse<Void>> response = handler.handleMissingServletRequestParameter(ex);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().getMessage()).contains("query");
  }

  /**
   * Verifies that {@link GlobalExceptionHandler#handleMaxUploadSizeExceeded} maps an over-limit
   * multipart upload to 400 — not the catch-all 500 an unhandled {@link
   * MaxUploadSizeExceededException} would otherwise fall through to. Exercised directly because a
   * MockMvc-driven request never triggers the servlet-level multipart size check that throws this
   * exception in production ({@code AiServiceIntegrationTest}'s speech-to-text tests cover the
   * reachable, successfully-transcribed paths instead).
   */
  @Test
  @DisplayName("handleMaxUploadSizeExceeded: maps MaxUploadSizeExceededException to 400")
  void handleMaxUploadSizeExceededMapsBadRequest() {
    var ex = new MaxUploadSizeExceededException(1_048_576L);

    ResponseEntity<ApiResponse<Void>> response = handler.handleMaxUploadSizeExceeded(ex);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().isSuccess()).isFalse();
  }

  /**
   * Verifies that {@link GlobalExceptionHandler#handleConstraintViolation} maps a constraint
   * violation on a {@code @Validated} controller method parameter (e.g. {@code k} on semantic
   * search) to 400.
   */
  @Test
  @DisplayName("handleConstraintViolation: maps ConstraintViolationException to 400")
  void handleConstraintViolationMapsBadRequest() {
    var ex = new jakarta.validation.ConstraintViolationException("k: must be greater than 0", null);

    ResponseEntity<ApiResponse<Void>> response = handler.handleConstraintViolation(ex);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().isSuccess()).isFalse();
  }

  /**
   * Verifies that {@link GlobalExceptionHandler#handleUnavailable} maps {@link
   * ServiceUnavailableException} to 503.
   */
  @Test
  @DisplayName("handleUnavailable: maps ServiceUnavailableException to 503")
  void handleUnavailableMapsServiceUnavailable() {
    var ex = new ServiceUnavailableException("Vosk model not ready");

    ResponseEntity<ApiResponse<Void>> response = handler.handleUnavailable(ex);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().getMessage()).isEqualTo("Vosk model not ready");
  }

  /**
   * Verifies that {@link GlobalExceptionHandler#handleUnexpected} maps an unanticipated exception
   * to 500 with a sanitized, generic message — the exception's own (potentially internal) message
   * never reaches the response body.
   */
  @Test
  @DisplayName("handleUnexpected: maps unexpected exceptions to 500 without leaking the message")
  void handleUnexpectedMapsInternalErrorWithoutLeakingMessage() {
    var ex = new NullPointerException("some internal null-pointer detail");

    ResponseEntity<ApiResponse<Void>> response = handler.handleUnexpected(ex);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().getMessage()).isEqualTo("An unexpected error occurred");
  }
}
