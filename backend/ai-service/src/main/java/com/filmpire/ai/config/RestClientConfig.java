package com.filmpire.ai.config;

import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * Provides a {@code @LoadBalanced} {@link RestClient.Builder} so
 * {@link com.filmpire.ai.client.MovieCatalogClient} can call
 * {@code lb://movie-service} and have Spring Cloud LoadBalancer resolve it
 * through Eureka, the same way the gateway resolves its own routes.
 */
@Configuration
public class RestClientConfig {

    @Bean
    @LoadBalanced
    public RestClient.Builder loadBalancedRestClientBuilder() {
        return RestClient.builder();
    }
}
