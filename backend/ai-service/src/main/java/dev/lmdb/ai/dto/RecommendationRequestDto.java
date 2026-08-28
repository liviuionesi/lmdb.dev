package dev.lmdb.ai.dto;

import java.util.List;
import java.util.UUID;

/**
 * Service-layer recommendation request handed to {@link dev.lmdb.ai.service.RecommendationService},
 * assembled by whichever transport received the call: the REST controller combines {@link
 * RecommendationRequestBodyDto} with the authenticated caller from the {@code X-User-Id} header,
 * and {@link dev.lmdb.ai.grpc.AiGrpcService} builds it from the proto message.
 *
 * <p>This is not a request body and carries no bean-validation constraints — each transport is
 * responsible for validating its own input before constructing one. {@code userId} is therefore
 * always an already-authenticated identity by the time it reaches here, never a caller-supplied
 * one.
 *
 * @param userId the authenticated user these recommendations are for, and whose taste profile the
 *     request refreshes
 * @param recentMovies titles the user recently watched/rated, used to build the recommendation
 *     prompt and the user's taste embedding
 * @param count how many recommendations to return
 */
public record RecommendationRequestDto(UUID userId, List<String> recentMovies, Integer count) {
  /** Applies the default page size when the caller omits {@code count}. */
  public int countOrDefault() {
    int c = count == null || count <= 0 ? 10 : count;
    return Math.min(c, 20);
  }
}
