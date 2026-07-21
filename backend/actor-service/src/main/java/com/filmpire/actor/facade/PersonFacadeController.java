package com.filmpire.actor.facade;

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

/**
 * TMDB v3-compatible person facade — actor-service's slice of the primary
 * API (ARCHITECTURE.md §5.1 row 8, issues #18/#32).
 *
 * <p>The Filmpire React app calls {@code GET /person/{id}} for actor pages;
 * this controller serves it byte-identical to TMDB via
 * {@link PersonFacadeService}. Discover-by-cast
 * ({@code /discover/movie?with_cast=}) is intentionally NOT here — it is a
 * movie-list endpoint and lives in movie-service's facade (#31).</p>
 */
@RestController
@Slf4j
@RequiredArgsConstructor
public class PersonFacadeController {

    /** TMDB's canonical not-found body (status_code 34), replayed locally. */
    private static final String TMDB_NOT_FOUND_BODY =
        "{\"success\":false,\"status_code\":34,"
        + "\"status_message\":\"The resource you requested could not be found.\"}";

    private final PersonFacadeService facadeService;

    /**
     * {@code GET /person/{id}} — person details in exact TMDB shape.
     *
     * @param id     numeric TMDB person id (non-numeric → local TMDB-shaped 404)
     * @param params forwarded query params (language, append_to_response, …)
     * @return raw TMDB person JSON
     */
    @GetMapping(value = "/person/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> personDetails(
            @PathVariable String id,
            @RequestParam MultiValueMap<String, String> params) {

        // Guard: person ids are numeric; anything else never reaches TMDB.
        if (!id.chars().allMatch(Character::isDigit)) {
            log.debug("Person facade: rejecting non-numeric id '{}'", id);
            return ResponseEntity.status(404)
                .contentType(MediaType.APPLICATION_JSON)
                .body(TMDB_NOT_FOUND_BODY);
        }

        return ok(facadeService.getRaw("person/" + id, strip(params)));
    }

    /**
     * Replays TMDB error responses byte-for-byte (status + body), per the
     * facade contract.
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
     * Maps "TMDB unreachable, no stored copy" to a TMDB-shaped 502.
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
     * Removes parameters that must never reach TMDB (client {@code api_key},
     * {@code session_id}).
     *
     * @param params raw client query params
     * @return a copy safe to forward and build cache keys from
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
