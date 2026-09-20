package dev.lmdb.ai.dto;

import java.util.List;

/**
 * Response body for {@code POST /api/v1/ai/search/execute}.
 *
 * @param results the matching movies; empty if nothing matched, never {@code null}
 * @param relaxedCriteria the parts of the query that were dropped to get these results, in the
 *     order they were dropped: "keywords", "genre", "collaborators" or "years". Empty when the
 *     whole query was used; never {@code null}.
 */
public record NaturalLanguageSearchResponseDto(
    List<SearchResultMovieDto> results, List<String> relaxedCriteria) {

  /** Defaults a missing {@code relaxedCriteria} to an empty list. */
  public NaturalLanguageSearchResponseDto {
    relaxedCriteria = relaxedCriteria == null ? List.of() : relaxedCriteria;
  }

  /**
   * Builds a response for a search that used the whole query.
   *
   * @param results the matching movies
   */
  public NaturalLanguageSearchResponseDto(List<SearchResultMovieDto> results) {
    this(results, List.of());
  }
}
