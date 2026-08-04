package com.filmpire.ai.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.loadbalancer.BlockingLoadBalancerInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * Configures a dedicated load-balanced {@link RestClient} for {@link
 * com.filmpire.ai.client.MovieCatalogClient} so calls to {@code lb://movie-service} resolve via
 * Eureka.
 *
 * <p>This deliberately does NOT declare a {@code @LoadBalanced RestClient.Builder} bean: Spring
 * Boot's {@code RestClientAutoConfiguration} only supplies its own ambient, unqualified {@code
 * RestClient.Builder} when {@code @ConditionalOnMissingBean(RestClient.Builder.class)} finds none —
 * a check that's type-only and ignores qualifiers. Defining a SECOND {@code RestClient.Builder}
 * bean here, even one qualified {@code @LoadBalanced}, suppresses that ambient bean entirely, so
 * every other unqualified {@code RestClient.Builder} injection in the app (Eureka's own
 * registration client, Spring AI's Ollama client) falls onto this one instead — both then try to
 * resolve their plain hostnames as Eureka service ids. Instead, {@link
 * BlockingLoadBalancerInterceptor} (the interface Spring Cloud LoadBalancer's auto-configured
 * interceptor bean always implements, whether or not Spring Retry ends up on the classpath) is
 * attached by hand to a fresh, private {@link RestClient.Builder} that only {@link
 * com.filmpire.ai.client.MovieCatalogClient} ever sees — no extra {@code RestClient.Builder} bean
 * is ever registered.
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
      BlockingLoadBalancerInterceptor loadBalancerInterceptor,
      @Value("${movie-service.base-url:lb://movie-service}") String movieServiceBaseUrl) {
    return RestClient.builder()
        .baseUrl(movieServiceBaseUrl)
        .requestInterceptor(loadBalancerInterceptor)
        .build();
  }
}
