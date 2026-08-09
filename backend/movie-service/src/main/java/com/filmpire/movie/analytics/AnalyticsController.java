package com.filmpire.movie.analytics;

import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Native analytics REST controller exposing aggregated request-count data derived from the {@code
 * tmdb.document.saved} Kafka event stream (#40, #41, Story #96).
 *
 * <p>This endpoint is not part of the TMDB v3 facade — it is Filmpire's own analytics API,
 * returning data aggregated by {@link TmdbAnalyticsConsumer} from the Kafka event stream into
 * MongoDB via idempotent upserts.
 */
@RestController
@RequestMapping("/api/v1/analytics")
@Slf4j
@RequiredArgsConstructor
public class AnalyticsController {

  private final RequestCountRepository requestCountRepository;

  /**
   * Returns the most-requested facade resources, ranked by descending request count.
   *
   * <p>Results are drawn from the {@code analytics_request_counts} MongoDB collection, populated in
   * real time by {@link TmdbAnalyticsConsumer}. The response list is bounded by the {@code limit}
   * query parameter (default 10, max 50).
   *
   * @param limit maximum number of entries to return (default 10, capped at 50)
   * @return ordered list of {@link RequestCount} documents with key, endpointType, and count
   */
  @GetMapping("/most-requested")
  public ResponseEntity<List<RequestCount>> getMostRequested(
      @RequestParam(defaultValue = "10") int limit) {
    int safeLimit = Math.min(Math.max(limit, 1), 50);
    log.debug("GET /api/v1/analytics/most-requested limit={}", safeLimit);
    List<RequestCount> results =
        requestCountRepository.findAllByOrderByCountDesc(PageRequest.of(0, safeLimit));
    return ResponseEntity.ok(results);
  }
}
