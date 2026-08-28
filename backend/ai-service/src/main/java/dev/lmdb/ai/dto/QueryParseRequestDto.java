package dev.lmdb.ai.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request body for both {@code POST /api/v1/ai/search/query} (parse only, #202) and {@code POST
 * /api/v1/ai/search/execute} (parse and execute, #203) — identical input, so one record covers
 * both.
 *
 * <p>Not user-scoped: unlike chat/recommendations, parsing or executing a query touches no per-user
 * data, so neither endpoint reads an {@code X-User-Id} identity at all (see {@link
 * dev.lmdb.ai.controller.AiController#parseQuery}/{@link
 * dev.lmdb.ai.controller.AiController#executeSearch}).
 *
 * @param query the raw natural-language search text, typed or transcribed from dictation
 */
public record QueryParseRequestDto(@NotBlank String query) {}
