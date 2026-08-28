package dev.lmdb.ai.dto;

import java.util.List;

/**
 * Request body for {@code POST /api/v1/ai/recommendations}.
 *
 * <p>Deliberately carries no user id: the caller's identity comes from the gateway-issued {@code
 * X-User-Id} header ({@link dev.lmdb.ai.security.CallerIdentity}), never from the body. Without
 * that, a caller could overwrite another user's {@link dev.lmdb.ai.model.UserTasteProfile}, which
 * this endpoint refreshes as a side effect. {@link RecommendationRequestDto} is the service-layer
 * request this is combined with that header to produce.
 *
 * @param recentMovies titles the user recently watched/rated, used to build the recommendation
 *     prompt and the user's taste embedding
 * @param count how many recommendations to return
 */
public record RecommendationRequestBodyDto(List<String> recentMovies, Integer count) {}
