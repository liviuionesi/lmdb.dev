package com.filmpire.movie.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

/**
 * Configuration for TMDB Http Client.
 */
@Configuration
public class TmdbClientConfig {

    @Value("${tmdb.api.base-url}")
    private String baseUrl;

    @Bean
    public TmdbClient tmdbClient(RestClient.Builder builder, @NonNull TmdbRateLimitInterceptor rateLimitInterceptor) {
        RestClient restClient = builder
            .baseUrl(baseUrl)
            .defaultHeader("Accept", "application/json")
            .defaultHeader("Content-Type", "application/json")
            .requestInterceptor(rateLimitInterceptor)
            .build();

        RestClientAdapter adapter = RestClientAdapter.create(restClient);
        HttpServiceProxyFactory factory = HttpServiceProxyFactory.builderFor(adapter).build();

        return factory.createClient(TmdbClient.class);
    }
}
