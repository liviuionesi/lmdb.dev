package com.filmpire.movie.client;

import feign.Logger;
import feign.RequestInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for TMDB Feign client.
 */
@Configuration
public class TmdbClientConfig {

    /**
     * Configure Feign logging level.
     *
     * @return Logger level
     */
    @Bean
    Logger.Level feignLoggerLevel() {
        return Logger.Level.BASIC;
    }

    /**
     * Request interceptor to add custom headers.
     *
     * @return Request interceptor
     */
    @Bean
    public RequestInterceptor requestInterceptor() {
        return requestTemplate -> {
            requestTemplate.header("Accept", "application/json");
            requestTemplate.header("Content-Type", "application/json");
        };
    }
}

