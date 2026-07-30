package com.filmpire.shared.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link ErrorResponse}.
 *
 * @see ErrorResponse
 */
@DisplayName("ErrorResponse Tests")
class ErrorResponseTest {

    @Test
    @DisplayName("of() builds a response with a non-null timestamp and no field errors")
    void of_shouldBuildSimpleResponse() {
        ErrorResponse response = ErrorResponse.of(404, "NOT_FOUND", "Movie not found", "/api/v1/movies/999");

        assertThat(response.getStatus()).isEqualTo(404);
        assertThat(response.getErrorCode()).isEqualTo("NOT_FOUND");
        assertThat(response.getMessage()).isEqualTo("Movie not found");
        assertThat(response.getPath()).isEqualTo("/api/v1/movies/999");
        assertThat(response.getTimestamp()).isNotNull();
        assertThat(response.getFieldErrors()).isNull();
    }

    @Test
    @DisplayName("withValidationErrors() carries the field errors through")
    void withValidationErrors_shouldCarryFieldErrors() {
        Map<String, String> fieldErrors = Map.of("title", "must not be blank");

        ErrorResponse response = ErrorResponse.withValidationErrors(
                400, "VALIDATION_ERROR", "Invalid request", "/api/v1/movies", fieldErrors);

        assertThat(response.getFieldErrors()).containsEntry("title", "must not be blank");
        assertThat(response.getTimestamp()).isNotNull();
    }

    /**
     * The no-args constructor plus the {@code @Builder.Default} timestamp
     * must still populate a timestamp — callers may bypass the {@code of()}
     * factory and build directly.
     */
    @Test
    @DisplayName("Builder without an explicit timestamp still defaults one")
    void builder_withoutExplicitTimestamp_shouldDefaultTimestamp() {
        ErrorResponse response = ErrorResponse.builder()
                .status(500)
                .errorCode("INTERNAL_ERROR")
                .message("Unexpected error")
                .build();

        assertThat(response.getTimestamp()).isNotNull();
    }
}
