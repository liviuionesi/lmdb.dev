package com.filmpire.movie.facade;

import lombok.Getter;

/**
 * Carries a non-2xx response received from the real TMDB API so it can be
 * propagated to the client byte-for-byte.
 *
 * <p>The TMDB v3 facade contract (ARCHITECTURE.md §5.1) requires that error
 * responses look exactly like TMDB's own errors — e.g. a request for a
 * non-existent movie must produce TMDB's {@code {"success":false,
 * "status_code":34,...}} body with HTTP 404, not a Filmpire-shaped error.
 * This exception transports the upstream status code and raw body from
 * {@link TmdbRawClient} to the {@link TmdbFacadeController} exception
 * handler, which replays them verbatim.</p>
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
