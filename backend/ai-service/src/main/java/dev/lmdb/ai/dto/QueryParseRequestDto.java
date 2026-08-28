package dev.lmdb.ai.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request body for {@code POST /api/v1/ai/search/query}.
 *
 * <p>Not user-scoped: unlike chat/recommendations, parsing a query touches no per-user data, so
 * this endpoint reads no {@code X-User-Id} identity at all (see {@link
 * dev.lmdb.ai.controller.AiController#parseQuery}).
 *
 * @param query the raw natural-language search text, typed or transcribed from dictation
 */
public record QueryParseRequestDto(@NotBlank String query) {}
