package com.filmpire.ai.repository;

import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Converts a Java {@code float[]} embedding to pgvector's text input format ({@code
 * "[0.1,0.2,...]"}) for binding into the native ANN query in {@link
 * UserTasteProfileRepository#findNearestNeighbours}.
 */
public final class EmbeddingFormat {

  private EmbeddingFormat() {}

  /**
   * @param embedding the vector to format
   * @return the pgvector text literal, e.g. {@code "[0.1,0.2,0.3]"}
   */
  public static String toPgvectorLiteral(float[] embedding) {
    return IntStream.range(0, embedding.length)
        .mapToObj(i -> Float.toString(embedding[i]))
        .collect(Collectors.joining(",", "[", "]"));
  }
}
