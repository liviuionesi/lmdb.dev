package com.filmpire.ai.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * Configures a dedicated {@code @LoadBalanced} {@link RestClient} for {@link
 * com.filmpire.ai.client.MovieCatalogClient} so calls to {@code lb://movie-service} resolve via
 * Eureka, while keeping the application-wide {@code RestClient.Builder} clean for Eureka's internal
 * registration client.
 *
 * <p>Spring Cloud LoadBalancer only post-processes {@link RestClient.Builder} beans qualified
 * {@code @LoadBalanced} — it never inspects an already-{@code build()}-ed {@link RestClient}. The
 * qualifier therefore has to sit on the builder bean below, not on the {@code RestClient} bean that
 * consumes it.
 */
@Configuration
public class RestClientConfig {

  /**
   * @return a {@link RestClient.Builder} that Spring Cloud LoadBalancer wires with its
   *     load-balancing request interceptor, resolving {@code lb://} URIs via Eureka
   */
  @Bean
  @LoadBalanced
  public RestClient.Builder loadBalancedRestClientBuilder() {
    return RestClient.builder();
  }

  /**
   * @param builder the load-balanced builder above, injected by its {@code @LoadBalanced}
   *     qualifier
   * @param movieServiceBaseUrl movie-service's base URL, {@code lb://movie-service} by default
   * @return the {@link RestClient} {@link com.filmpire.ai.client.MovieCatalogClient} calls
   *     movie-service through
   */
  @Bean
  public RestClient movieServiceRestClient(
      @LoadBalanced RestClient.Builder builder,
      @Value("${movie-service.base-url:lb://movie-service}") String movieServiceBaseUrl) {
    return builder.baseUrl(movieServiceBaseUrl).build();
  }
}
