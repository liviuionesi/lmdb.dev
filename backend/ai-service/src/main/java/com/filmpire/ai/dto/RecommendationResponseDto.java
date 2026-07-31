package com.filmpire.ai.dto;

import java.util.List;

/**
 * Response body for {@code POST /api/v1/ai/recommendations}.
 *
 * @param recommendations the ranked recommendation list, most relevant first
 */
public record RecommendationResponseDto(List<MovieRecommendationDto> recommendations) {}
