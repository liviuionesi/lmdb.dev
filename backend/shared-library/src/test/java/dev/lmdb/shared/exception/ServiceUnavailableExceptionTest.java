package dev.lmdb.shared.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link ServiceUnavailableException}.
 *
 * @see ServiceUnavailableException
 */
@DisplayName("ServiceUnavailableException Tests")
class ServiceUnavailableExceptionTest {

  @Test
  @DisplayName("Message-only constructor leaves serviceName null")
  void messageOnlyConstructor_shouldLeaveServiceNameNull() {
    ServiceUnavailableException ex = new ServiceUnavailableException("temporarily down");

    assertThat(ex.getMessage()).isEqualTo("temporarily down");
    assertThat(ex.getServiceName()).isNull();
  }

  @Test
  @DisplayName("ServiceName+message constructor formats a standard message")
  void serviceNameAndMessageConstructor_shouldFormatMessage() {
    ServiceUnavailableException ex = new ServiceUnavailableException("tmdb", "connection refused");

    assertThat(ex.getMessage()).isEqualTo("tmdb service is unavailable: connection refused");
    assertThat(ex.getServiceName()).isEqualTo("tmdb");
  }

  @Test
  @DisplayName("Full constructor formats the message and preserves the cause")
  void fullConstructor_shouldFormatMessageAndPreserveCause() {
    Throwable cause = new IllegalStateException("root cause");

    ServiceUnavailableException ex =
        new ServiceUnavailableException("tmdb", "connection refused", cause);

    assertThat(ex.getMessage()).isEqualTo("tmdb service is unavailable: connection refused");
    assertThat(ex.getServiceName()).isEqualTo("tmdb");
    assertThat(ex.getCause()).isSameAs(cause);
  }
}
