package com.filmpire.ai.dto;

import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

/**
 * Request body for {@code POST /api/v1/ai/recommendations}.
 *
 * @param userId       the requesting user (user-service id)
 * @param recentMovies titles the user recently watched/rated, used to build
 *                      the recommendation prompt and the user's taste embedding
 * @param count         how many recommendations to return
 */
public record RecommendationRequestDto(
    @NotNull UUID userId,
    List<String> recentMovies,
    Integer count
) {
    /** Applies the default page size when the caller omits {@code count}. */
    public int countOrDefault() {
        return count == null || count <= 0 ? 10 : count;
    }
}
