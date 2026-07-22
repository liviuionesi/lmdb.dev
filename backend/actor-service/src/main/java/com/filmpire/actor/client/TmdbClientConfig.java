package com.filmpire.actor.client;

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
 * {@link TmdbPersonClient} on top of it — the single typed HTTP interface
 * used by both the native {@code /api/v1/actors} API and the TMDB v3 facade
 * ({@code com.filmpire.actor.facade}) as of ADR-010, mirroring
 * movie-service's client config.</p>
 */
@Configuration
public class TmdbClientConfig {

    /** TMDB API base URL (e.g. {@code https://api.themoviedb.org/3}). */
    @Value("${tmdb.api.base-url}")
    private String baseUrl;

    /**
     * The shared HTTP client for all TMDB traffic from this service:
     * base URL, JSON headers, and the rate-limit interceptor.
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
     * Typed TMDB person HTTP interface backed by the shared RestClient.
     *
     * @param tmdbRestClient the shared client bean defined above
     * @return proxy implementing {@link TmdbPersonClient}
     */
    @Bean
    public TmdbPersonClient tmdbPersonClient(RestClient tmdbRestClient) {
        RestClientAdapter adapter = RestClientAdapter.create(tmdbRestClient);
        HttpServiceProxyFactory factory = HttpServiceProxyFactory.builderFor(adapter).build();

        return factory.createClient(TmdbPersonClient.class);
    }
}
