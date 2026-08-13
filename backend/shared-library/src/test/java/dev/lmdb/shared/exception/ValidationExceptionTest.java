package dev.lmdb.shared.exception;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link ValidationException}.
 *
 * @see ValidationException
 */
@DisplayName("ValidationException Tests")
class ValidationExceptionTest {

  @Test
  @DisplayName("Message-only constructor starts with no field errors")
  void messageOnlyConstructor_shouldHaveNoFieldErrors() {
    ValidationException ex = new ValidationException("invalid request");

    assertThat(ex.getMessage()).isEqualTo("invalid request");
    assertThat(ex.getFieldErrors()).isEmpty();
    assertThat(ex.hasFieldErrors()).isFalse();
  }

  @Test
  @DisplayName("Message+fieldErrors constructor keeps the supplied map")
  void messageAndFieldErrorsConstructor_shouldKeepSuppliedMap() {
    ValidationException ex =
        new ValidationException("invalid request", Map.of("title", "must not be blank"));

    assertThat(ex.getFieldErrors()).containsEntry("title", "must not be blank");
    assertThat(ex.hasFieldErrors()).isTrue();
  }

  /**
   * A null map must not leak as a null getFieldErrors() — callers rely on an always-non-null map to
   * iterate without a null check.
   */
  @Test
  @DisplayName("Message+null fieldErrors constructor defaults to an empty map")
  void messageAndNullFieldErrorsConstructor_shouldDefaultToEmptyMap() {
    ValidationException ex = new ValidationException("invalid request", (Map<String, String>) null);

    assertThat(ex.getFieldErrors()).isNotNull().isEmpty();
  }

  @Test
  @DisplayName("Field+errorMessage constructor formats a field-specific message")
  void fieldAndErrorMessageConstructor_shouldFormatMessage() {
    ValidationException ex = new ValidationException("title", "must not be blank");

    assertThat(ex.getMessage()).isEqualTo("Validation failed for field 'title': must not be blank");
    assertThat(ex.getFieldErrors()).containsEntry("title", "must not be blank");
    assertThat(ex.hasFieldErrors()).isTrue();
  }
}
