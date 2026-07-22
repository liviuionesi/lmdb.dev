package com.filmpire.actor.client;

import com.filmpire.actor.client.dto.TmdbPersonMovieCreditsResponse;
import com.filmpire.actor.client.dto.TmdbPersonResponse;
import com.filmpire.actor.client.dto.TmdbPersonSearchResponse;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

/**
 * Typed HTTP client for the TMDB person endpoints. Backs both the native
 * {@code /api/v1/actors} API and the TMDB-shaped facade (ADR-010) — there is
 * one client and one persisted dataset behind both, matching movie-service's
 * pattern.
 */
@HttpExchange
public interface TmdbPersonClient {

    /**
     * Get person (actor) details by TMDB id.
     *
     * @param personId TMDB person id
     * @param apiKey   server-side TMDB API key
     * @return person details
     */
    @GetExchange("/person/{personId}")
    TmdbPersonResponse getPersonDetails(
        @PathVariable("personId") Long personId,
        @RequestParam("api_key") String apiKey
    );

    /**
     * Get a person's movie cast/crew credits.
     *
     * @param personId TMDB person id
     * @param apiKey   server-side TMDB API key
     * @return movie credits
     */
    @GetExchange("/person/{personId}/movie_credits")
    TmdbPersonMovieCreditsResponse getPersonMovieCredits(
        @PathVariable("personId") Long personId,
        @RequestParam("api_key") String apiKey
    );

    /**
     * Search people by name.
     *
     * @param apiKey server-side TMDB API key
     * @param query  free-text name query
     * @param page   page number (1-based)
     * @return paged person summaries
     */
    @GetExchange("/search/person")
    TmdbPersonSearchResponse searchPersons(
        @RequestParam("api_key") String apiKey,
        @RequestParam("query") String query,
        @RequestParam(value = "page", defaultValue = "1") Integer page
    );
}
