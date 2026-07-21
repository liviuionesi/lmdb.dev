package com.filmpire.movie.facade;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.util.UriUtils;

import java.net.URI;
import java.nio.charset.StandardCharsets;

/**
 * Low-level HTTP client that fetches raw JSON from the real TMDB API.
 *
 * <p>Unlike {@link com.filmpire.movie.client.TmdbClient} (which deserializes
 * into typed DTOs for the native {@code /api/v1} API), this client returns
 * the response body as an untouched {@code String}. The TMDB v3 facade
 * (ARCHITECTURE.md §5.1) serves that string verbatim, guaranteeing
 * shape-identical responses.</p>
 *
 * <p>Requests go through the shared {@code RestClient} bean, which carries
 * the {@link com.filmpire.movie.client.TmdbRateLimitInterceptor} — so raw
 * facade traffic and typed client traffic share one rate-limit bucket
 * (TMDB allows 40 requests / 10 s per instance).</p>
 */
@Component
@Slf4j
public class TmdbRawClient {

    /** Shared, rate-limited HTTP client for all TMDB traffic. */
    private final RestClient restClient;

    /** Server-side TMDB API key — never accepted from, or exposed to, clients. */
    private final String apiKey;

    /**
     * TMDB base URL. Also configured on the shared RestClient, but needed
     * here again because this client builds a fully ABSOLUTE, pre-encoded
     * {@link URI} — see {@link #buildUri} for why.
     */
    private final String baseUrl;

    /**
     * Creates the raw client.
     *
     * @param tmdbRestClient shared RestClient bean (rate limiting), defined
     *                       in {@link com.filmpire.movie.client.TmdbClientConfig}
     * @param apiKey         server-side TMDB API key from configuration
     * @param baseUrl        TMDB API base URL from configuration
     */
    public TmdbRawClient(RestClient tmdbRestClient,
                         @Value("${tmdb.api.key}") String apiKey,
                         @Value("${tmdb.api.base-url}") String baseUrl) {
        this.restClient = tmdbRestClient;
        this.apiKey = apiKey;
        this.baseUrl = baseUrl;
    }

    /**
     * Fetches a TMDB endpoint and returns the raw JSON body.
     *
     * @param path   TMDB path without leading slash (e.g. {@code movie/popular});
     *               callers pass only whitelisted/validated paths
     * @param params query parameters to forward (decoded values, as Spring
     *               binds them); must already have the client-sent
     *               {@code api_key} stripped
     * @return the exact JSON body TMDB returned
     * @throws TmdbUpstreamException if TMDB responds with a non-2xx status;
     *                               carries the upstream status and body for
     *                               verbatim passthrough
     * @throws org.springframework.web.client.ResourceAccessException
     *         if TMDB is unreachable (network failure) — callers may fall
     *         back to a stale stored copy
     */
    public String fetch(String path, MultiValueMap<String, String> params) {
        URI uri = buildUri(path, params);
        log.debug("Fetching raw TMDB response: {}", path);

        try {
            // Passing a pre-built absolute URI: RestClient uses it as-is,
            // with NO template processing — the body is taken as String so
            // it is never re-serialized either.
            return restClient.get()
                .uri(uri)
                .retrieve()
                .body(String.class);
        } catch (RestClientResponseException e) {
            // Non-2xx from TMDB: wrap status + body so the controller can
            // replay TMDB's own error response to the client unchanged.
            throw new TmdbUpstreamException(
                e.getStatusCode().value(),
                e.getResponseBodyAsString()
            );
        }
    }

    /**
     * Builds the absolute, correctly-encoded request URI.
     *
     * <p>Spring binds incoming query params DECODED (a search for
     * {@code fight club} arrives as the literal string with a space). Naively
     * passing them through a URI template re-interprets special characters —
     * spaces, {@code &}, {@code =}, {@code +}, non-ASCII — and corrupts the
     * forwarded query (regression test:
     * {@code searchQueryEncodingSurvivesForwarding}). So each parameter name
     * and value is percent-encoded explicitly with
     * {@link UriUtils#encodeQueryParam}, and the builder is told the result
     * is already encoded ({@code build(true)}).</p>
     *
     * @param path   whitelisted TMDB path (plain ASCII by construction)
     * @param params decoded query params to forward
     * @return absolute URI, safe to send as-is
     */
    private URI buildUri(String path, MultiValueMap<String, String> params) {
        UriComponentsBuilder builder = UriComponentsBuilder
            .fromUriString(baseUrl)
            .path("/" + path);

        // 1. Re-encode every forwarded param explicitly (handles space, &, =,
        //    +, and UTF-8 like "amélie" correctly).
        if (params != null) {
            params.forEach((name, values) -> values.forEach(value ->
                builder.queryParam(
                    UriUtils.encodeQueryParam(name, StandardCharsets.UTF_8),
                    UriUtils.encodeQueryParam(value, StandardCharsets.UTF_8))));
        }

        // 2. Server-side API key (alphanumeric — already valid encoded form).
        builder.queryParam("api_key", apiKey);

        // 3. build(true) = "components are pre-encoded, do not touch them".
        return builder.build(true).toUri();
    }
}
