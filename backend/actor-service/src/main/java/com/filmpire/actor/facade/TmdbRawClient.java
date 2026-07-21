package com.filmpire.actor.facade;

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
 * Low-level HTTP client that fetches raw JSON from the real TMDB API and
 * returns the body as an untouched {@code String} (byte-fidelity contract,
 * ADR-003).
 *
 * <p>Mirrors movie-service's client of the same name, INCLUDING its
 * query-encoding lesson: parameters are percent-encoded explicitly and the
 * request is sent as a pre-built absolute {@link URI}, never through
 * RestClient's URI-template path (which corrupts spaces/UTF-8 — see the
 * {@code searchQueryEncodingSurvivesForwarding} regression test in
 * movie-service).</p>
 */
@Component
@Slf4j
public class TmdbRawClient {

    /** Shared, rate-limited HTTP client for all TMDB traffic. */
    private final RestClient restClient;

    /** Server-side TMDB API key — never accepted from, or exposed to, clients. */
    private final String apiKey;

    /** TMDB base URL, needed to build absolute pre-encoded URIs. */
    private final String baseUrl;

    /**
     * Creates the raw client.
     *
     * @param tmdbRestClient shared RestClient bean (rate limiting), defined
     *                       in {@link com.filmpire.actor.client.TmdbClientConfig}
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
     * @param path   TMDB path without leading slash (e.g. {@code person/819});
     *               callers pass only whitelisted/validated paths
     * @param params query parameters to forward (decoded values); must already
     *               have the client-sent {@code api_key} stripped
     * @return the exact JSON body TMDB returned
     * @throws TmdbUpstreamException on a non-2xx TMDB response (carries the
     *                               upstream status and body for passthrough)
     * @throws org.springframework.web.client.ResourceAccessException
     *         if TMDB is unreachable — callers may fall back to a stale copy
     */
    public String fetch(String path, MultiValueMap<String, String> params) {
        URI uri = buildUri(path, params);
        log.debug("Fetching raw TMDB response: {}", path);

        try {
            // Absolute pre-encoded URI: no template processing; String body:
            // no re-serialization.
            return restClient.get()
                .uri(uri)
                .retrieve()
                .body(String.class);
        } catch (RestClientResponseException e) {
            // Non-2xx from TMDB → replayable error.
            throw new TmdbUpstreamException(
                e.getStatusCode().value(),
                e.getResponseBodyAsString()
            );
        }
    }

    /**
     * Builds the absolute, correctly-encoded request URI. See the class
     * Javadoc for why explicit encoding is mandatory here.
     *
     * @param path   whitelisted TMDB path (plain ASCII by construction)
     * @param params decoded query params to forward
     * @return absolute URI, safe to send as-is
     */
    private URI buildUri(String path, MultiValueMap<String, String> params) {
        UriComponentsBuilder builder = UriComponentsBuilder
            .fromUriString(baseUrl)
            .path("/" + path);

        // 1. Percent-encode every forwarded name/value (space, &, =, +, UTF-8).
        if (params != null) {
            params.forEach((name, values) -> values.forEach(value ->
                builder.queryParam(
                    UriUtils.encodeQueryParam(name, StandardCharsets.UTF_8),
                    UriUtils.encodeQueryParam(value, StandardCharsets.UTF_8))));
        }

        // 2. Server-side API key (alphanumeric — already valid encoded form).
        builder.queryParam("api_key", apiKey);

        // 3. build(true): components are pre-encoded, leave them untouched.
        return builder.build(true).toUri();
    }
}
