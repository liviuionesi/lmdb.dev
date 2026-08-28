package dev.lmdb.actor.client;

import dev.lmdb.actor.client.dto.TmdbPersonImagesResponse;
import dev.lmdb.actor.client.dto.TmdbPersonMovieCreditsResponse;
import dev.lmdb.actor.client.dto.TmdbPersonResponse;
import dev.lmdb.actor.client.dto.TmdbPersonSearchResponse;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

/**
 * Typed HTTP client for the TMDB person endpoints. Backs both the native {@code /api/v1/actors} API
 * and the TMDB-shaped facade (ADR-010) — there is one client and one persisted dataset behind both,
 * matching movie-service's pattern.
 *
 * <p>The TMDB API key is injected transparently by the {@link
 * org.springframework.web.client.RestClient} interceptor configured in {@link TmdbClientConfig}, so
 * these method signatures carry only business-relevant parameters.
 */
@HttpExchange
public interface TmdbPersonClient {

  /**
   * Get person (actor) details by TMDB id.
   *
   * @param personId TMDB person id
   * @return person details
   */
  @GetExchange("/person/{personId}")
  TmdbPersonResponse getPersonDetails(@PathVariable("personId") Long personId);

  /**
   * Get a person's movie cast/crew credits.
   *
   * @param personId TMDB person id
   * @return movie credits
   */
  @GetExchange("/person/{personId}/movie_credits")
  TmdbPersonMovieCreditsResponse getPersonMovieCredits(@PathVariable("personId") Long personId);

  /**
   * Search people by name.
   *
   * @param query free-text name query
   * @param page page number (1-based)
   * @return paged person summaries
   */
  @GetExchange("/search/person")
  TmdbPersonSearchResponse searchPersons(
      @RequestParam("query") String query,
      @RequestParam(value = "page", defaultValue = "1") Integer page);

  /**
   * Get every profile image TMDB holds for a person.
   *
   * @param personId TMDB person id
   * @return profile-image references and their metadata
   */
  @GetExchange("/person/{personId}/images")
  TmdbPersonImagesResponse getPersonImages(@PathVariable("personId") Long personId);

  /**
   * Get the currently popular people, TMDB's ranking.
   *
   * @param page page number (1-based)
   * @return paged person summaries
   */
  @GetExchange("/person/popular")
  TmdbPersonSearchResponse getPopularPersons(
      @RequestParam(value = "page", defaultValue = "1") Integer page);
}
