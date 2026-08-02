package com.filmpire.ai.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * Configures a dedicated {@code @LoadBalanced} {@link RestClient} for
 * {@link com.filmpire.ai.client.MovieCatalogClient} so calls to {@code lb://movie-service}
 * resolve via Eureka, while keeping the application-wide {@code RestClient.Builder}
 * clean for Eureka's internal registration client.
 */
@Configuration
public class RestClientConfig {

    @Bean
    @LoadBalanced
    public RestClient movieServiceRestClient(
            RestClient.Builder builder,
            @Value("${movie-service.base-url:lb://movie-service}") String movieServiceBaseUrl) {
        return builder.baseUrl(movieServiceBaseUrl).build();
    }
}
