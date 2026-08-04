package com.filmpire.ai.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.loadbalancer.LoadBalancerInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * Configures a dedicated load-balanced {@link RestClient} for {@link
 * com.filmpire.ai.client.MovieCatalogClient} so calls to {@code lb://movie-service} resolve via
 * Eureka.
 *
 * <p>This deliberately does NOT declare a {@code @LoadBalanced RestClient.Builder} bean: Spring
 * Boot applies every {@code RestClientCustomizer} (which is how Spring Cloud LoadBalancer wires
 * {@code @LoadBalanced} support) to the shared, application-wide {@link RestClient.Builder} —
 * scoping load balancing to one qualified bean doesn't stop it there, it makes every other {@code
 * RestClient.Builder} in the app load-balanced too, including Eureka's own registration client and
 * Spring AI's Ollama client, both of which call plain hostnames, not service ids. Instead, {@link
 * LoadBalancerInterceptor} (unconditionally auto-configured whenever Spring Cloud LoadBalancer is
 * on the classpath) is attached by hand to a fresh, private {@link RestClient.Builder} that only
 * {@link com.filmpire.ai.client.MovieCatalogClient} ever sees.
 */
@Configuration
public class RestClientConfig {

  /**
   * @param loadBalancerInterceptor resolves {@code lb://} URIs to a real movie-service instance via
   *     Eureka
   * @param movieServiceBaseUrl movie-service's base URL, {@code lb://movie-service} by default
   * @return the {@link RestClient} {@link com.filmpire.ai.client.MovieCatalogClient} calls
   *     movie-service through — load-balanced on its own, not via the shared builder
   */
  @Bean
  public RestClient movieServiceRestClient(
      LoadBalancerInterceptor loadBalancerInterceptor,
      @Value("${movie-service.base-url:lb://movie-service}") String movieServiceBaseUrl) {
    return RestClient.builder()
        .baseUrl(movieServiceBaseUrl)
        .requestInterceptor(loadBalancerInterceptor)
        .build();
  }
}
