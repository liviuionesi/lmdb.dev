package com.filmpire.movie.client;

import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

/**
 * HTTP client configuration for the TMDB API.
 *
 * <p>Defines one shared, rate-limited {@link RestClient} and builds
 * {@link TmdbClient} on top of it — the single typed HTTP interface used by
 * both the native {@code /api/v1} API and the TMDB v3 facade
 * ({@code com.filmpire.movie.facade}) as of ADR-010; there is no longer a
 * separate raw/untyped client.</p>
 *
 * <p>The {@link TmdbRateLimitInterceptor} carried by the shared
 * {@code RestClient} holds the token bucket, so all outbound TMDB traffic
 * from this service instance is throttled by ONE bucket.</p>
 */
@Configuration
public class TmdbClientConfig {

    /** TMDB API base URL (e.g. {@code https://api.themoviedb.org/3}). */
    @Value("${tmdb.api.base-url}")
    private String baseUrl;

    /**
     * The single shared HTTP client for all TMDB traffic: base URL, JSON
     * headers, and the rate-limit interceptor.
     *
     * @param builder              Spring-provided builder
     * @param rateLimitInterceptor bucket-based throttle (40 req / 10 s)
     * @return shared TMDB RestClient
     */
    @Bean
    public RestClient tmdbRestClient(RestClient.Builder builder,
                                     @NonNull TmdbRateLimitInterceptor rateLimitInterceptor) {
        return builder
            .baseUrl(baseUrl)
            .defaultHeader("Accept", "application/json")
            .defaultHeader("Content-Type", "application/json")
            .requestInterceptor(rateLimitInterceptor)
            .build();
    }

    /**
     * Typed TMDB HTTP interface backed by the shared RestClient.
     *
     * @param tmdbRestClient the shared client bean defined above
     * @return proxy implementing {@link TmdbClient}
     */
    @Bean
    public TmdbClient tmdbClient(RestClient tmdbRestClient) {
        RestClientAdapter adapter = RestClientAdapter.create(tmdbRestClient);
        HttpServiceProxyFactory factory = HttpServiceProxyFactory.builderFor(adapter).build();

        return factory.createClient(TmdbClient.class);
    }
}
