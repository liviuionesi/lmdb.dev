package com.filmpire.shared.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link ResourceNotFoundException}.
 *
 * @see ResourceNotFoundException
 */
@DisplayName("ResourceNotFoundException Tests")
class ResourceNotFoundExceptionTest {

    /**
     * The plain-message constructor is for callers who already built their
     * own message; resourceType/resourceId are not derivable from it and
     * must stay null rather than guess.
     */
    @Test
    @DisplayName("Message-only constructor leaves resourceType/resourceId null")
    void messageOnlyConstructor_shouldLeaveResourceFieldsNull() {
        ResourceNotFoundException ex = new ResourceNotFoundException("not found");

        assertThat(ex.getMessage()).isEqualTo("not found");
        assertThat(ex.getResourceType()).isNull();
        assertThat(ex.getResourceId()).isNull();
    }

    @Test
    @DisplayName("Type+id constructor formats a standard message")
    void typeAndIdConstructor_shouldFormatMessage() {
        ResourceNotFoundException ex = new ResourceNotFoundException("Movie", "550");

        assertThat(ex.getMessage()).isEqualTo("Movie with id '550' not found");
        assertThat(ex.getResourceType()).isEqualTo("Movie");
        assertThat(ex.getResourceId()).isEqualTo("550");
    }

    @Test
    @DisplayName("Type+field+value constructor formats a field-specific message")
    void typeFieldValueConstructor_shouldFormatMessage() {
        ResourceNotFoundException ex = new ResourceNotFoundException("Movie", "tmdbId", 550L);

        assertThat(ex.getMessage()).isEqualTo("Movie with tmdbId '550' not found");
        assertThat(ex.getResourceType()).isEqualTo("Movie");
        assertThat(ex.getResourceId()).isEqualTo("550");
    }
}
