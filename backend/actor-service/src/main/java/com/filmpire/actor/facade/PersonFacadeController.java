package com.filmpire.actor.facade;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.filmpire.actor.client.dto.TmdbPersonMovieCreditsResponse;
import com.filmpire.actor.client.dto.TmdbPersonResponse;
import com.filmpire.actor.model.Actor;
import com.filmpire.actor.service.ActorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;

/**
 * TMDB v3-compatible person facade — actor-service's slice of the primary
 * API (ARCHITECTURE.md §5.1 row 8, issues #18/#32).
 *
 * <p>The Filmpire React app calls {@code GET /person/{id}} for actor pages.
 * As of ADR-010 (supersedes ADR-003), this serves TMDB's exact field
 * names/shape but the data behind it is {@link ActorService}'s persisted,
 * mapped {@link Actor} catalog — not a raw cached copy of TMDB's bytes.
 * Discover-by-cast ({@code /discover/movie?with_cast=}) is intentionally
 * NOT here — it is a movie-list endpoint and lives in movie-service's
 * facade (#31).</p>
 */
@RestController
@Slf4j
@RequiredArgsConstructor
public class PersonFacadeController {

    private final ActorService actorService;

    /**
     * {@code GET /person/{id}} — person details in TMDB's exact shape,
     * read-through/save-through against the persisted {@link Actor} catalog.
     *
     * @param id numeric TMDB person id (non-numeric → local TMDB-shaped 404)
     * @return TMDB-shaped person JSON
     */
    @GetMapping(value = "/person/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Object> personDetails(@PathVariable String id) {
        if (!id.chars().allMatch(Character::isDigit)) {
            log.debug("Person facade: rejecting non-numeric id '{}'", id);
            return notFound();
        }

        Actor actor = actorService.getOrFetchActorEntity(Long.parseLong(id));
        return ResponseEntity.ok(toTmdbPersonResponse(actor));
    }

    /**
     * {@code GET /person/{id}/movie_credits} — the actor's filmography in
     * TMDB's exact shape. Always live: the referenced movies belong to
     * movie-service's own database (ADR-002).
     *
     * @param id numeric TMDB person id
     * @return TMDB-shaped movie-credits JSON
     */
    @GetMapping(value = "/person/{id}/movie_credits", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Object> movieCredits(@PathVariable String id) {
        if (!id.chars().allMatch(Character::isDigit)) {
            log.debug("Person facade: rejecting non-numeric id '{}'", id);
            return notFound();
        }

        TmdbPersonMovieCreditsResponse credits = actorService.getFilmographyRaw(Long.parseLong(id));
        return ResponseEntity.ok(credits);
    }

    /**
     * Replays a real TMDB error response byte-for-byte: {@link com.filmpire.actor.client.TmdbPersonClient}
     * calls go through Spring's RestClient, which captures the upstream body
     * on a non-2xx response — reusing it preserves TMDB's exact error shape.
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
     * body.
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
     * Builds TMDB's exact person-detail shape from our persisted entity.
     *
     * @param actor the persisted actor
     * @return TMDB-shaped person response
     */
    private static TmdbPersonResponse toTmdbPersonResponse(Actor actor) {
        return new TmdbPersonResponse(
            actor.getTmdbId(),
            actor.getName(),
            actor.getBiography(),
            actor.getBirthDate(),
            actor.getBirthPlace(),
            actor.getProfilePath(),
            actor.getPopularity(),
            actor.getAlsoKnownAs(),
            actor.getKnownForDepartment(),
            actor.getGender(),
            actor.getImdbId(),
            actor.getHomepage(),
            actor.getAdult()
        );
    }

    private static ResponseEntity<Object> notFound() {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(new TmdbErrorResponse(false, 34, "The resource you requested could not be found."));
    }

    /**
     * TMDB's own error envelope shape, replayed for locally-detected error
     * cases that don't have a captured upstream body to forward verbatim.
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
