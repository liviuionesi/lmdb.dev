package com.filmpire.actor.client;

import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * HTTP client configuration for the TMDB API.
 *
 * <p>Actor-service only needs the raw passthrough client (person data is
 * served byte-identical per ADR-003), so unlike movie-service there is no
 * typed HTTP interface here — just the single rate-limited
 * {@link RestClient} the facade's {@code TmdbRawClient} builds on.</p>
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
}
