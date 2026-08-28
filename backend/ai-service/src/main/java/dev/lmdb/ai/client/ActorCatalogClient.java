package dev.lmdb.ai.client;

import dev.lmdb.ai.dto.QueryFilterRole;
import dev.lmdb.shared.dto.ApiResponse;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Calls actor-service (via Eureka/{@code lb://}) to resolve the person/role side of a structured
 * natural-language query filter (#203, ADR-020): name-to-id resolution, cast filmography (the
 * default "is this person in the movie" signal), and role-specific crew credits (director/producer,
 * #217). Follows {@link MovieCatalogClient}'s established shape — a dedicated client per downstream
 * service, graceful empty-result degradation on failure so one dependency being down never fails
 * the whole search request.
 *
 * <p>Unlike movie-service's native API, actor-service wraps every response in the shared {@link
 * ApiResponse} envelope — every method here deserializes that envelope and unwraps {@code data}.
 */
@Component
@Slf4j
public class ActorCatalogClient {

  /**
   * Bound on a single cast-filmography fetch, mirroring {@link
   * MovieCatalogClient#discoverMovieIdsInYearRange}'s own stated limit — most people's cast
   * filmography fits well inside this; a person with more (a prolific character actor) is a known,
   * accepted gap rather than an unbounded pagination loop.
   */
  private static final int CAST_RESULT_CAP = 200;

  private final RestClient restClient;

  /**
   * @param actorServiceRestClient the load-balanced client from {@link
   *     dev.lmdb.ai.config.RestClientConfig}, already resolving {@code lb://actor-service} via
   *     Eureka
   */
  public ActorCatalogClient(
      @Qualifier("actorServiceRestClient") RestClient actorServiceRestClient) {
    this.restClient = actorServiceRestClient;
  }

  /**
   * Resolves a free-text person name to a TMDB person id via actor-service's name search, taking
   * the first (most relevant, per TMDB's own ranking) match — the same "don't re-implement
   * relevance ranking" posture the rest of this service already takes toward TMDB-backed search.
   *
   * @param name free-text person name (e.g. #202's {@code personName} or a {@code collaborators}
   *     entry)
   * @return the matched person's TMDB id, or empty if no match was found or actor-service is
   *     unreachable — the caller treats both the same way: this constraint resolves to no movies,
   *     not an error
   */
  public Optional<Long> findPersonId(String name) {
    try {
      ApiResponse<PersonSearchResult> response =
          restClient
              .get()
              .uri(
                  uriBuilder ->
                      uriBuilder
                          .path("/api/v1/actors/search")
                          .queryParam("query", name)
                          .queryParam("page", 1)
                          .build())
              .retrieve()
              .body(new ParameterizedTypeReference<ApiResponse<PersonSearchResult>>() {});
      List<PersonSummary> results =
          response == null || response.getData() == null || response.getData().results() == null
              ? List.of()
              : response.getData().results();
      return results.isEmpty() ? Optional.empty() : Optional.ofNullable(results.get(0).tmdbId());
    } catch (Exception e) {
      log.warn("actor-service unreachable while resolving person name: {}", e.getMessage());
      return Optional.empty();
    }
  }

  /**
   * Fetches a person's cast filmography (movies they acted in) — the default "is this person in the
   * movie" signal used both when a query names no explicit role and to resolve a collaborator
   * constraint (#203 AC2), since a collaborator is asserted as a co-star, not a co-director.
   *
   * @param personId TMDB person id
   * @return up to {@link #CAST_RESULT_CAP} cast credits, or empty if actor-service is unreachable —
   *     degrading rather than failing the whole search request
   */
  public List<PersonCredit> fetchCastCredits(Long personId) {
    try {
      ApiResponse<FilmographyPage> response =
          restClient
              .get()
              .uri(
                  uriBuilder ->
                      uriBuilder
                          .path("/api/v1/actors/{id}/movies")
                          .queryParam("page", 1)
                          .queryParam("size", CAST_RESULT_CAP)
                          .build(personId))
              .retrieve()
              .body(new ParameterizedTypeReference<ApiResponse<FilmographyPage>>() {});
      return response == null || response.getData() == null || response.getData().results() == null
          ? List.of()
          : response.getData().results();
    } catch (Exception e) {
      log.warn(
          "actor-service unreachable while fetching cast credits for person {}: {}",
          personId,
          e.getMessage());
      return List.of();
    }
  }

  /**
   * Fetches a person's crew credits for a specific director/producer role, via #217's endpoint —
   * used when the structured filter names an explicit {@code DIRECTED}/{@code PRODUCED} role,
   * whether that role is the query's positive constraint or the set being excluded under negation
   * (#203 AC1/AC3).
   *
   * @param personId TMDB person id
   * @param role the crew role to filter by; {@code ACTED} is not a crew role — callers wanting cast
   *     credits use {@link #fetchCastCredits} instead, so this returns empty for it rather than
   *     silently calling the wrong endpoint
   * @return matching crew credits, or empty if {@code role} is {@code ACTED}/{@code null} or
   *     actor-service is unreachable
   */
  public List<PersonCredit> fetchCrewCredits(Long personId, QueryFilterRole role) {
    String job = toTmdbCrewJob(role);
    if (job == null) {
      return List.of();
    }
    try {
      ApiResponse<List<PersonCredit>> response =
          restClient
              .get()
              .uri(
                  uriBuilder ->
                      uriBuilder
                          .path("/api/v1/actors/{id}/crew")
                          .queryParam("job", job)
                          .build(personId))
              .retrieve()
              .body(new ParameterizedTypeReference<ApiResponse<List<PersonCredit>>>() {});
      return response == null || response.getData() == null ? List.of() : response.getData();
    } catch (Exception e) {
      log.warn(
          "actor-service unreachable while fetching {} crew credits for person {}: {}",
          job,
          personId,
          e.getMessage());
      return List.of();
    }
  }

  /**
   * Maps a {@link QueryFilterRole} to the exact TMDB {@code job} string #217's crew endpoint
   * filters by.
   *
   * @param role the structured filter's role, possibly {@code null}
   * @return the matching TMDB job name, or {@code null} for {@code ACTED}/{@code null} (neither is
   *     a crew role)
   */
  private static String toTmdbCrewJob(QueryFilterRole role) {
    if (role == QueryFilterRole.DIRECTED) {
      return "Director";
    }
    if (role == QueryFilterRole.PRODUCED) {
      return "Producer";
    }
    return null;
  }
}
