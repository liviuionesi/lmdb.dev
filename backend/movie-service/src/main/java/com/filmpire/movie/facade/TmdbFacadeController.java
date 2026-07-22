package com.filmpire.movie.facade;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.filmpire.movie.client.dto.TmdbCreditsResponse;
import com.filmpire.movie.client.dto.TmdbGenresResponse;
import com.filmpire.movie.client.dto.TmdbMovieListResponse;
import com.filmpire.movie.client.dto.TmdbMovieResponse;
import com.filmpire.movie.client.dto.TmdbVideosResponse;
import com.filmpire.movie.model.Cast;
import com.filmpire.movie.model.Credits;
import com.filmpire.movie.model.Crew;
import com.filmpire.movie.model.Movie;
import com.filmpire.movie.model.Video;
import com.filmpire.movie.service.MovieService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * TMDB v3-compatible facade controller — the primary API of the platform.
 *
 * <p>Exposes the exact TMDB v3 paths the Filmpire React app calls (extracted
 * from its {@code src/services/TMDB.js}; see ARCHITECTURE.md §5.1), with
 * TMDB's own field names and response envelopes preserved. As of ADR-010,
 * the data behind these responses is our own persisted, mapped
 * {@link Movie} catalog (via {@link MovieService}) rather than a raw cached
 * copy of TMDB's bytes: detail lookups are read-through/save-through
 * against MongoDB, and list endpoints call TMDB live for ranking while
 * upserting every result they see. Only the wire shape is TMDB's; the data
 * is ours.</p>
 *
 * <p>Auth/account endpoints ({@code /authentication/**}, {@code /account/**})
 * are NOT here — those are being retargeted at Filmpire's own user-service
 * JWT flow rather than proxied to real TMDB (see the follow-up to #33).</p>
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

    private final MovieService movieService;

    /**
     * {@code GET /genre/movie/list} — the genre catalog (React app sidebar).
     *
     * @return TMDB-shaped genre list
     */
    @GetMapping(value = "/genre/movie/list", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<TmdbGenresResponse> genreList() {
        return ResponseEntity.ok(movieService.getGenresRaw());
    }

    /**
     * {@code GET /movie/{idOrCategory}} — TMDB overloads this path position:
     * a fixed category name (popular, top_rated, upcoming, now_playing)
     * yields a movie list, while a numeric id yields movie details (the
     * React app requests details with {@code append_to_response=videos,credits}).
     *
     * @param idOrCategory     category name or numeric TMDB movie id
     * @param page             page number, for the category-list case
     * @param appendToResponse comma-separated sub-resources to embed (videos, credits)
     * @return TMDB-shaped movie list or movie detail JSON
     */
    @GetMapping(value = "/movie/{idOrCategory}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Object> movieByIdOrCategory(
            @PathVariable String idOrCategory,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(name = "append_to_response", required = false) String appendToResponse) {

        // 1. Fixed category name → volatile list endpoint.
        if (MOVIE_CATEGORIES.contains(idOrCategory)) {
            return ResponseEntity.ok(movieService.getMovieCategoryRaw(idOrCategory, page));
        }

        // 2. Numeric id → near-immutable movie details, read-through/save-through.
        if (idOrCategory.chars().allMatch(Character::isDigit)) {
            Long id = Long.parseLong(idOrCategory);
            Set<String> appends = parseAppendToResponse(appendToResponse);
            Movie movie = movieService.getMovieForFacade(id, appends);
            return ResponseEntity.ok(toTmdbMovieResponse(movie, appends));
        }

        // 3. Neither → replay TMDB's own not-found error shape.
        log.debug("TMDB facade: unknown movie category/id '{}'", idOrCategory);
        return notFound();
    }

    /**
     * {@code GET /movie/{id}/recommendations} — recommendations for a movie
     * (React app details page).
     *
     * @param id   numeric TMDB movie id
     * @param page page number
     * @return TMDB-shaped movie list
     */
    @GetMapping(value = "/movie/{id}/recommendations", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<TmdbMovieListResponse> recommendations(
            @PathVariable Long id,
            @RequestParam(defaultValue = "1") int page) {
        return ResponseEntity.ok(movieService.getRecommendedMoviesRaw(id, page));
    }

    /**
     * {@code GET /movie/{id}/similar} — similar movies (React app details
     * page fallback when recommendations are empty).
     *
     * @param id   numeric TMDB movie id
     * @param page page number
     * @return TMDB-shaped movie list
     */
    @GetMapping(value = "/movie/{id}/similar", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<TmdbMovieListResponse> similar(
            @PathVariable Long id,
            @RequestParam(defaultValue = "1") int page) {
        return ResponseEntity.ok(movieService.getSimilarMoviesRaw(id, page));
    }

    /**
     * {@code GET /discover/movie} — filtered discovery. Covers both React app
     * uses: by genre ({@code with_genres}) and by cast member
     * ({@code with_cast}).
     *
     * @param page      page number
     * @param genreId   {@code with_genres} filter
     * @param year      release-year filter
     * @param minRating {@code vote_average.gte} filter
     * @param castId    {@code with_cast} filter (TMDB person id)
     * @return TMDB-shaped movie list
     */
    @GetMapping(value = "/discover/movie", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<TmdbMovieListResponse> discover(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(name = "with_genres", required = false) Long genreId,
            @RequestParam(required = false) Integer year,
            @RequestParam(name = "vote_average.gte", required = false) Double minRating,
            @RequestParam(name = "with_cast", required = false) Long castId) {
        return ResponseEntity.ok(movieService.discoverMoviesRaw(page, genreId, year, minRating, castId));
    }

    /**
     * {@code GET /search/movie} — free-text search.
     *
     * @param query search query
     * @param page  page number
     * @return TMDB-shaped movie list
     */
    @GetMapping(value = "/search/movie", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<TmdbMovieListResponse> search(
            @RequestParam String query,
            @RequestParam(defaultValue = "1") int page) {
        return ResponseEntity.ok(movieService.searchMoviesRaw(query, page));
    }

    /**
     * Replays a real TMDB error response byte-for-byte: {@link TmdbClient}
     * calls go through Spring's RestClient, which captures the upstream
     * body on a non-2xx response — reusing it preserves TMDB's exact error
     * shape without us having to construct it.
     *
     * @param e the captured upstream HTTP error
     * @return response mirroring TMDB's error exactly
     */
    @ExceptionHandler(RestClientResponseException.class)
    public ResponseEntity<String> upstreamError(RestClientResponseException e) {
        return ResponseEntity.status(e.getStatusCode())
            .contentType(MediaType.APPLICATION_JSON)
            .body(e.getResponseBodyAsString());
    }

    /**
     * Maps "TMDB unreachable" to 502 Bad Gateway with a TMDB-shaped error
     * body, so even total-failure responses keep the contract's shape.
     *
     * @param e network failure reaching TMDB
     * @return 502 response with TMDB-style error JSON
     */
    @ExceptionHandler(ResourceAccessException.class)
    public ResponseEntity<TmdbErrorResponse> tmdbUnreachable(ResourceAccessException e) {
        log.error("TMDB unreachable: {}", e.getMessage());
        return ResponseEntity.status(502)
            .body(new TmdbErrorResponse(false, 502, "Upstream TMDB API is unreachable."));
    }

    /**
     * Parses TMDB's {@code append_to_response} query param into a set of
     * requested sub-resource names.
     *
     * @param appendToResponse raw comma-separated param value, or null
     * @return requested sub-resource names (empty if none requested)
     */
    private static Set<String> parseAppendToResponse(String appendToResponse) {
        if (appendToResponse == null || appendToResponse.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(appendToResponse.split(","))
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .collect(java.util.stream.Collectors.toSet());
    }

    /**
     * Builds TMDB's exact detail response shape from our persisted entity,
     * embedding videos/credits only when requested (and thus populated by
     * {@link MovieService#getMovieForFacade}) — matching TMDB's own
     * behavior of only including {@code videos}/{@code credits} keys when
     * {@code append_to_response} asked for them.
     *
     * @param movie   the persisted movie
     * @param appends requested sub-resources
     * @return TMDB-shaped movie detail response
     */
    private static TmdbMovieResponse toTmdbMovieResponse(Movie movie, Set<String> appends) {
        return new TmdbMovieResponse(
            movie.getTmdbId(),
            movie.getTitle(),
            movie.getOriginalTitle(),
            movie.getOverview(),
            movie.getPosterPath(),
            movie.getBackdropPath(),
            movie.getReleaseDate(),
            movie.getVoteAverage(),
            movie.getVoteCount(),
            movie.getGenres(),
            movie.getRuntime(),
            movie.getStatus(),
            movie.getBudget(),
            movie.getRevenue(),
            movie.getSpokenLanguages(),
            movie.getProductionCompanies(),
            movie.getProductionCountries(),
            movie.getBelongsToCollection(),
            movie.getVideo(),
            movie.getOriginalLanguage(),
            movie.getPopularity(),
            movie.getAdult(),
            movie.getImdbId(),
            movie.getTagline(),
            movie.getHomepage(),
            appends.contains("videos") ? toTmdbVideosResponse(movie) : null,
            appends.contains("credits") ? toTmdbCreditsResponse(movie) : null
        );
    }

    private static TmdbVideosResponse toTmdbVideosResponse(Movie movie) {
        List<Video> videos = movie.getVideos() != null ? movie.getVideos() : List.of();
        return new TmdbVideosResponse(
            movie.getTmdbId(),
            videos.stream()
                .map(v -> new TmdbVideosResponse.TmdbVideo(
                    v.getId(), v.getKey(), v.getName(), v.getSite(),
                    v.getSize(), v.getType(), v.getOfficial(), v.getPublishedAt()))
                .toList()
        );
    }

    private static TmdbCreditsResponse toTmdbCreditsResponse(Movie movie) {
        Credits credits = movie.getCredits();
        List<Cast> cast = credits != null && credits.getCast() != null ? credits.getCast() : List.of();
        List<Crew> crew = credits != null && credits.getCrew() != null ? credits.getCrew() : List.of();
        return new TmdbCreditsResponse(
            movie.getTmdbId(),
            cast.stream()
                .map(c -> new TmdbCreditsResponse.TmdbCast(
                    c.getId(), c.getName(), c.getCharacter(), c.getProfilePath(), c.getOrder(), null))
                .toList(),
            crew.stream()
                .map(c -> new TmdbCreditsResponse.TmdbCrew(
                    c.getId(), c.getName(), c.getJob(), c.getDepartment(), c.getProfilePath(), null))
                .toList()
        );
    }

    private static ResponseEntity<Object> notFound() {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(new TmdbErrorResponse(false, 34, "The resource you requested could not be found."));
    }

    /**
     * TMDB's own error envelope shape, replayed for locally-detected error
     * cases (unknown category/id, upstream unreachable) that don't have a
     * captured upstream body to forward verbatim.
     *
     * @param success       always false
     * @param statusCode    TMDB's numeric status code (not the HTTP status)
     * @param statusMessage human-readable message
     */
    private record TmdbErrorResponse(
        boolean success,
        @JsonProperty("status_code") int statusCode,
        @JsonProperty("status_message") String statusMessage
    ) {
    }
}
