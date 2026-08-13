package dev.lmdb.shared.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link UnauthorizedException}.
 *
 * @see UnauthorizedException
 */
@DisplayName("UnauthorizedException Tests")
class UnauthorizedExceptionTest {

  /**
   * Verify that the message constructor correctly sets the exception message and leaves the cause
   * as null.
   */
  @Test
  @DisplayName("Message constructor sets the message")
  void messageConstructor_shouldSetMessage() {
    UnauthorizedException ex = new UnauthorizedException("token expired");

    assertThat(ex.getMessage()).isEqualTo("token expired");
    assertThat(ex.getCause()).isNull();
  }

  /**
   * Verify that the message and cause constructor correctly sets the message and preserves the
   * underlying cause.
   */
  @Test
  @DisplayName("Message+cause constructor preserves the cause")
  void messageAndCauseConstructor_shouldPreserveCause() {
    Throwable cause = new IllegalStateException("root cause");

    UnauthorizedException ex = new UnauthorizedException("token expired", cause);

    assertThat(ex.getCause()).isSameAs(cause);
  }
}
