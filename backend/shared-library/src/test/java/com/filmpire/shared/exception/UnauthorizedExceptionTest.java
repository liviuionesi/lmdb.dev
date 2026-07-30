package com.filmpire.shared.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link UnauthorizedException}.
 *
 * @see UnauthorizedException
 */
@DisplayName("UnauthorizedException Tests")
class UnauthorizedExceptionTest {

    @Test
    @DisplayName("Message constructor sets the message")
    void messageConstructor_shouldSetMessage() {
        UnauthorizedException ex = new UnauthorizedException("token expired");

        assertThat(ex.getMessage()).isEqualTo("token expired");
        assertThat(ex.getCause()).isNull();
    }

    @Test
    @DisplayName("Message+cause constructor preserves the cause")
    void messageAndCauseConstructor_shouldPreserveCause() {
        Throwable cause = new IllegalStateException("root cause");

        UnauthorizedException ex = new UnauthorizedException("token expired", cause);

        assertThat(ex.getCause()).isSameAs(cause);
    }
}
