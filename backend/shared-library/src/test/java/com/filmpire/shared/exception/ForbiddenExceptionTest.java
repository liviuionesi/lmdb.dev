package com.filmpire.shared.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link ForbiddenException}.
 *
 * @see ForbiddenException
 */
@DisplayName("ForbiddenException Tests")
class ForbiddenExceptionTest {

    /**
     * Verify that the message constructor correctly sets the exception message
     * and leaves the cause as null.
     */
    @Test
    @DisplayName("Message constructor sets the message")
    void messageConstructor_shouldSetMessage() {
        ForbiddenException ex = new ForbiddenException("not allowed");

        assertThat(ex.getMessage()).isEqualTo("not allowed");
        assertThat(ex.getCause()).isNull();
    }

    /**
     * Verify that the message and cause constructor correctly sets the message
     * and preserves the underlying cause.
     */
    @Test
    @DisplayName("Message+cause constructor preserves the cause")
    void messageAndCauseConstructor_shouldPreserveCause() {
        Throwable cause = new IllegalStateException("root cause");

        ForbiddenException ex = new ForbiddenException("not allowed", cause);

        assertThat(ex.getMessage()).isEqualTo("not allowed");
        assertThat(ex.getCause()).isSameAs(cause);
    }
}
