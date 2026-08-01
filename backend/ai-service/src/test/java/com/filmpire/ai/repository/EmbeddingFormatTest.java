package com.filmpire.ai.repository;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link EmbeddingFormat}: verifies the pgvector text
 * literal produced matches pgvector's expected input syntax exactly, since
 * {@link UserTasteProfileRepository#findNearestNeighbours} casts it
 * directly in SQL with no further validation.
 */
class EmbeddingFormatTest {

    @Test
    @DisplayName("formats a vector as a bracketed, comma-separated pgvector literal")
    void formatsVectorAsPgvectorLiteral() {
        // Given a small vector, When formatted, Then it matches pgvector's "[v1,v2,v3]" syntax.
        String literal = EmbeddingFormat.toPgvectorLiteral(new float[] {0.1f, 0.2f, 0.3f});

        assertThat(literal).isEqualTo("[0.1,0.2,0.3]");
    }

    @Test
    @DisplayName("formats an empty vector as an empty pgvector literal")
    void formatsEmptyVectorAsEmptyLiteral() {
        String literal = EmbeddingFormat.toPgvectorLiteral(new float[0]);

        assertThat(literal).isEqualTo("[]");
    }
}
