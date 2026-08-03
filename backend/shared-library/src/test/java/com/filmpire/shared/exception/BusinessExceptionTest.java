package com.filmpire.shared.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link BusinessException}.
 *
 * @see BusinessException
 */
@DisplayName("BusinessException Tests")
class BusinessExceptionTest {

  /**
   * The single-arg constructor must default the error code rather than leave it null, since callers
   * use it for client-side error handling.
   */
  @Test
  @DisplayName("Single-message constructor defaults the error code")
  void singleMessageConstructor_shouldDefaultErrorCode() {
    BusinessException ex = new BusinessException("rule violated");

    assertThat(ex.getMessage()).isEqualTo("rule violated");
    assertThat(ex.getErrorCode()).isEqualTo("BUSINESS_ERROR");
  }

  /** The two-arg constructor must use the caller-supplied error code instead of the default. */
  @Test
  @DisplayName("Message+errorCode constructor keeps the supplied code")
  void messageAndErrorCodeConstructor_shouldKeepSuppliedCode() {
    BusinessException ex = new BusinessException("rule violated", "CUSTOM_CODE");

    assertThat(ex.getErrorCode()).isEqualTo("CUSTOM_CODE");
  }

  /** The three-arg constructor must preserve both the error code and the chained cause. */
  @Test
  @DisplayName("Message+errorCode+cause constructor preserves the cause")
  void fullConstructor_shouldPreserveCause() {
    Throwable cause = new IllegalStateException("root cause");

    BusinessException ex = new BusinessException("rule violated", "CUSTOM_CODE", cause);

    assertThat(ex.getErrorCode()).isEqualTo("CUSTOM_CODE");
    assertThat(ex.getCause()).isSameAs(cause);
  }
}
