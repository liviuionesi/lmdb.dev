package com.filmpire.movie.facade;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.ResourceAccessException;

import java.util.Set;

/**
 * TMDB v3-compatible facade controller — the primary API of the platform.
 *
 * <p>Exposes the exact TMDB v3 paths the Filmpire React app calls (extracted
 * from its {@code src/services/TMDB.js}; see ARCHITECTURE.md §5.1), returning
 * raw TMDB-shaped JSON via {@link TmdbFacadeService}'s read-through cache.
 * The app's {@code api_key} query parameter is accepted and discarded; the
 * real key is injected server-side by {@link TmdbRawClient}.</p>
 *
 * <p>Auth/account endpoints ({@code /authentication/**}, {@code /account/**})
 * are NOT here — the API gateway proxies those straight to the real TMDB
 * (issue #33).</p>
 */
@RestController
@Slf4j
@RequiredArgsConstructor
public class TmdbFacadeController {

    /**
     * TMDB's fixed movie-list categories under {@code /movie/{category}}.
     * Anything else in that path position must be a numeric movie id.
     */
    private static final Set<String> MOVIE_CATEGORIES =
        Set.of("popular", "top_rated", "upcoming", "now_playing");

    /**
     * TMDB's own "resource not found" error body, replayed for requests that
     * match no known category or id form (status_code 34 is TMDB's constant
     * for a missing resource).
     */
    private static final String TMDB_NOT_FOUND_BODY =
        "{\"success\":false,\"status_code\":34,"
        + "\"status_message\":\"The resource you requested could not be found.\"}";

    private final TmdbFacadeService facadeService;

    /**
     * {@code GET /genre/movie/list} — the genre catalog (React app sidebar).
     * Genres change essentially never, so the detail freshness window applies.
     *
     * @param params forwarded query params (e.g. {@code language})
     * @return raw TMDB genre list JSON
     */
    @GetMapping(value = "/genre/movie/list", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> genreList(@RequestParam MultiValueMap<String, String> params) {
        return ok(facadeService.getDetail("genre/movie/list", strip(params)));
    }

    /**
     * {@code GET /movie/{idOrCategory}} — TMDB overloads this path position:
     * a fixed category name (popular, top_rated, upcoming, now_playing)
     * yields a movie list, while a numeric id yields movie details (the React
     * app requests details with {@code append_to_response=videos,credits},
     * which is forwarded like any other param).
     *
     * @param idOrCategory category name or numeric TMDB movie id
     * @param params       forwarded query params (page, append_to_response, …)
     * @return raw TMDB JSON (list or details)
     */
    @GetMapping(value = "/movie/{idOrCategory}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> movieByIdOrCategory(
            @PathVariable String idOrCategory,
            @RequestParam MultiValueMap<String, String> params) {

        MultiValueMap<String, String> clean = strip(params);

        // 1. Fixed category name → volatile list endpoint.
        if (MOVIE_CATEGORIES.contains(idOrCategory)) {
            return ok(facadeService.getList("movie/" + idOrCategory, clean));
        }

        // 2. Numeric id → near-immutable movie details.
        if (idOrCategory.chars().allMatch(Character::isDigit)) {
            return ok(facadeService.getDetail("movie/" + idOrCategory, clean));
        }

        // 3. Neither → replay TMDB's own not-found error shape.
        log.debug("TMDB facade: unknown movie category/id '{}'", idOrCategory);
        return ResponseEntity.status(404)
            .contentType(MediaType.APPLICATION_JSON)
            .body(TMDB_NOT_FOUND_BODY);
    }

    /**
     * {@code GET /movie/{id}/recommendations} — recommendations for a movie
     * (React app details page).
     *
     * @param id     numeric TMDB movie id
     * @param params forwarded query params (page, …)
     * @return raw TMDB movie-list JSON
     */
    @GetMapping(value = "/movie/{id}/recommendations", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> recommendations(
            @PathVariable String id,
            @RequestParam MultiValueMap<String, String> params) {
        return ok(facadeService.getList("movie/" + id + "/recommendations", strip(params)));
    }

    /**
     * {@code GET /movie/{id}/similar} — similar movies (React app details
     * page fallback when recommendations are empty).
     *
     * @param id     numeric TMDB movie id
     * @param params forwarded query params (page, …)
     * @return raw TMDB movie-list JSON
     */
    @GetMapping(value = "/movie/{id}/similar", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> similar(
            @PathVariable String id,
            @RequestParam MultiValueMap<String, String> params) {
        return ok(facadeService.getList("movie/" + id + "/similar", strip(params)));
    }

    /**
     * {@code GET /discover/movie} — filtered discovery. Covers both React app
     * uses: by genre ({@code with_genres}) and by cast member
     * ({@code with_cast}); all filters are forwarded verbatim.
     *
     * @param params forwarded query params (with_genres, with_cast, page, …)
     * @return raw TMDB movie-list JSON
     */
    @GetMapping(value = "/discover/movie", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> discover(@RequestParam MultiValueMap<String, String> params) {
        return ok(facadeService.getList("discover/movie", strip(params)));
    }

    /**
     * {@code GET /search/movie} — free-text search.
     *
     * @param params forwarded query params (query, page, …)
     * @return raw TMDB movie-list JSON
     */
    @GetMapping(value = "/search/movie", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> search(@RequestParam MultiValueMap<String, String> params) {
        return ok(facadeService.getList("search/movie", strip(params)));
    }

    /**
     * Replays TMDB error responses (4xx/5xx) to the client byte-for-byte,
     * preserving TMDB's status code and error body per the facade contract.
     *
     * @param e upstream error captured by {@link TmdbRawClient}
     * @return response mirroring TMDB's error exactly
     */
    @ExceptionHandler(TmdbUpstreamException.class)
    public ResponseEntity<String> upstreamError(TmdbUpstreamException e) {
        return ResponseEntity.status(e.getStatusCode())
            .contentType(MediaType.APPLICATION_JSON)
            .body(e.getBody());
    }

    /**
     * Maps "TMDB unreachable and no stored copy" to 502 Bad Gateway with a
     * TMDB-shaped error body, so even total-failure responses keep the
     * contract's shape.
     *
     * @param e network failure from {@link TmdbRawClient}
     * @return 502 response with TMDB-style error JSON
     */
    @ExceptionHandler(ResourceAccessException.class)
    public ResponseEntity<String> tmdbUnreachable(ResourceAccessException e) {
        log.error("TMDB unreachable and no local copy available: {}", e.getMessage());
        return ResponseEntity.status(502)
            .contentType(MediaType.APPLICATION_JSON)
            .body("{\"success\":false,\"status_code\":502,"
                + "\"status_message\":\"Upstream TMDB API is unreachable.\"}");
    }

    /**
     * Removes parameters that must never reach the real TMDB: the client-sent
     * {@code api_key} (replaced by the server-side key) and {@code session_id}
     * (only meaningful on account endpoints, which the gateway proxies).
     *
     * @param params raw client query params
     * @return a copy safe to forward and to build cache keys from
     */
    private static MultiValueMap<String, String> strip(MultiValueMap<String, String> params) {
        MultiValueMap<String, String> clean = new LinkedMultiValueMap<>(params);
        clean.remove("api_key");
        clean.remove("session_id");
        return clean;
    }

    /**
     * Wraps a raw JSON body in a 200 response with the JSON content type.
     *
     * @param body raw TMDB-shaped JSON
     * @return 200 OK response
     */
    private static ResponseEntity<String> ok(String body) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(body);
    }
}
