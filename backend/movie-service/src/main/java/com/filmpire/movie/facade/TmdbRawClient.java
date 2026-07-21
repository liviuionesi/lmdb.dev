package com.filmpire.movie.facade;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;

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

    /** Shared, rate-limited HTTP client configured with the TMDB base URL. */
    private final RestClient restClient;

    /** Server-side TMDB API key — never accepted from, or exposed to, clients. */
    private final String apiKey;

    /**
     * Creates the raw client.
     *
     * @param tmdbRestClient shared RestClient bean (base URL + rate limiting),
     *                       defined in {@link com.filmpire.movie.client.TmdbClientConfig}
     * @param apiKey         server-side TMDB API key from configuration
     */
    public TmdbRawClient(RestClient tmdbRestClient, @Value("${tmdb.api.key}") String apiKey) {
        this.restClient = tmdbRestClient;
        this.apiKey = apiKey;
    }

    /**
     * Fetches a TMDB endpoint and returns the raw JSON body.
     *
     * @param path   TMDB path without leading slash (e.g. {@code movie/popular})
     * @param params query parameters to forward; must already have the
     *               client-sent {@code api_key} stripped
     * @return the exact JSON body TMDB returned
     * @throws TmdbUpstreamException if TMDB responds with a non-2xx status;
     *                               carries the upstream status and body for
     *                               verbatim passthrough
     * @throws org.springframework.web.client.ResourceAccessException
     *         if TMDB is unreachable (network failure) — callers may fall
     *         back to a stale stored copy
     */
    public String fetch(String path, MultiValueMap<String, String> params) {
        // 1. Build the URI: forwarded client params + the server-side api_key.
        //    UriComponentsBuilder keeps the exact param encoding TMDB expects.
        URI uri = UriComponentsBuilder.fromPath("/" + path)
            .queryParams(params)
            .queryParam("api_key", apiKey)
            .build()
            .toUri();

        log.debug("Fetching raw TMDB response for path: {}", path);

        try {
            // 2. Execute; body is taken as String so it is never re-serialized.
            return restClient.get()
                .uri(uri.toString())
                .retrieve()
                .body(String.class);
        } catch (RestClientResponseException e) {
            // 3. Non-2xx from TMDB: wrap status + body so the controller can
            //    replay TMDB's own error response to the client unchanged.
            throw new TmdbUpstreamException(
                e.getStatusCode().value(),
                e.getResponseBodyAsString()
            );
        }
    }
}
