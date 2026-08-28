package dev.lmdb.ai.client;

import dev.lmdb.shared.dto.PageResponse;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Calls movie-service's own persisted catalog (via Eureka/{@code lb://}), never TMDB directly:
 * {@code /api/v1/movies/popular} for recommendation candidates, {@code /api/v1/movies/search} for
 * the natural-language search feature's plain-title fallback, and {@code /api/v1/movies/discover}
 * to resolve a release-year range (#203, ADR-020).
 */
@Component
@Slf4j
public class MovieCatalogClient {

  private final RestClient restClient;

  /**
   * @param movieServiceRestClient the load-balanced client from {@link
   *     dev.lmdb.ai.config.RestClientConfig}, already resolving {@code lb://movie-service} via
   *     Eureka — explicitly qualified since {@link dev.lmdb.ai.config.RestClientConfig} now
   *     declares a second named {@link RestClient} bean for actor-service (#203)
   */
  public MovieCatalogClient(
      @Qualifier("movieServiceRestClient") RestClient movieServiceRestClient) {
    this.restClient = movieServiceRestClient;
  }

  /**
   * Fetches a page of currently popular movies as the candidate pool for recommendations.
   *
   * @param count how many candidates to request
   * @return the candidate movies, or an empty list if movie-service is unreachable (recommendations
   *     degrade gracefully rather than failing the whole request)
   */
  public List<CandidateMovie> fetchCandidates(int count) {
    try {
      PageResponse<CandidateMovie> page =
          restClient
              .get()
              .uri(
                  uriBuilder ->
                      uriBuilder
                          .path("/api/v1/movies/popular")
                          .queryParam("page", 1)
                          .queryParam("size", count)
                          .build())
              .retrieve()
              .body(new ParameterizedTypeReference<PageResponse<CandidateMovie>>() {});
      return page == null || page.getContent() == null ? List.of() : page.getContent();
    } catch (Exception e) {
      log.warn(
          "movie-service unreachable while fetching recommendation candidates: {}", e.getMessage());
      return List.of();
    }
  }

  /**
   * Delegates a plain-title query to movie-service's existing title search, unchanged (#198 AC3,
   * #203 AC4) — this client applies no filtering or ranking of its own.
   *
   * @param query the literal title text
   * @param count how many results to request
   * @return matching movies, most relevant first as movie-service ranks them; empty if
   *     movie-service is unreachable, degrading rather than failing the whole search request
   */
  public List<MovieListItem> searchByTitle(String query, int count) {
    try {
      PageResponse<MovieListItem> page =
          restClient
              .get()
              .uri(
                  uriBuilder ->
                      uriBuilder
                          .path("/api/v1/movies/search")
                          .queryParam("query", query)
                          .queryParam("page", 1)
                          .queryParam("size", count)
                          .build())
              .retrieve()
              .body(new ParameterizedTypeReference<PageResponse<MovieListItem>>() {});
      return page == null || page.getContent() == null ? List.of() : page.getContent();
    } catch (Exception e) {
      log.warn("movie-service unreachable while searching by title: {}", e.getMessage());
      return List.of();
    }
  }

  /**
   * Resolves which movie ids fall within a release-year range, via movie-service's {@code
   * /discover} endpoint (#218) — the aggregation step intersects this set against a person's credit
   * movie ids to apply a year-range constraint (#203).
   *
   * <p>Bounded to {@code count} results rather than paging through movie-service's full discover
   * result set — a deliberate, stated limit (matching {@link
   * dev.lmdb.ai.security.PromptSanitizer}'s own capped-list philosophy elsewhere in this service),
   * not an oversight: a year range this narrow rarely has more than a few hundred TMDB releases,
   * and the caller already intersects against a much smaller, person-specific credit list.
   *
   * @param yearFrom inclusive range start, or {@code null} for no lower bound
   * @param yearTo inclusive range end, or {@code null} for no upper bound
   * @param count how many discover results to request (the bound described above)
   * @return the movie ids TMDB reports in this range; empty if movie-service is unreachable,
   *     degrading rather than failing the whole search request
   */
  public Set<Long> discoverMovieIdsInYearRange(Integer yearFrom, Integer yearTo, int count) {
    try {
      PageResponse<MovieListItem> page =
          restClient
              .get()
              .uri(
                  uriBuilder -> {
                    uriBuilder
                        .path("/api/v1/movies/discover")
                        .queryParam("page", 1)
                        .queryParam("size", count);
                    // 1. Only attach each bound if the caller actually gave one — movie-service
                    //    treats an absent param as "no constraint on this side," not zero.
                    if (yearFrom != null) {
                      uriBuilder.queryParam("yearFrom", yearFrom);
                    }
                    if (yearTo != null) {
                      uriBuilder.queryParam("yearTo", yearTo);
                    }
                    return uriBuilder.build();
                  })
              .retrieve()
              .body(new ParameterizedTypeReference<PageResponse<MovieListItem>>() {});
      return page == null || page.getContent() == null
          ? Set.of()
          : page.getContent().stream().map(MovieListItem::tmdbId).collect(Collectors.toSet());
    } catch (Exception e) {
      log.warn("movie-service unreachable while resolving a year-range filter: {}", e.getMessage());
      return Set.of();
    }
  }
}
