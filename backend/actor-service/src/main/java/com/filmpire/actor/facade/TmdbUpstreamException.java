package com.filmpire.actor.facade;

import lombok.Getter;

/**
 * Carries a non-2xx response received from the real TMDB API so it can be
 * propagated to the client byte-for-byte (facade contract, ADR-003 /
 * ARCHITECTURE.md §5.1). Mirrors movie-service's exception of the same
 * name — deliberate duplication across service boundaries.
 */
@Getter
public class TmdbUpstreamException extends RuntimeException {

    /** HTTP status code returned by TMDB (e.g. 404, 401, 429). */
    private final int statusCode;

    /** Raw JSON error body returned by TMDB, replayed to the client as-is. */
    private final String body;

    /**
     * Creates the exception from an upstream TMDB error response.
     *
     * @param statusCode HTTP status code TMDB responded with
     * @param body       raw response body TMDB responded with (may be empty,
     *                   never {@code null})
     */
    public TmdbUpstreamException(int statusCode, String body) {
        super("TMDB upstream returned HTTP " + statusCode);
        this.statusCode = statusCode;
        this.body = body == null ? "" : body;
    }
}
