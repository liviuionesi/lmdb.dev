package dev.lmdb.ai.dto;

import java.util.List;

/**
 * Response body for {@code POST /api/v1/ai/search/execute} (#203).
 *
 * @param results the matching movies; empty if nothing matched, never {@code null}
 */
public record NaturalLanguageSearchResponseDto(List<SearchResultMovieDto> results) {}
