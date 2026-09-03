package dev.lmdb.ai.config;

import java.net.URI;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.loadbalancer.BlockingLoadBalancerInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * Configures dedicated {@link RestClient} beans for {@link dev.lmdb.ai.client.MovieCatalogClient}
 * and {@link dev.lmdb.ai.client.ActorCatalogClient} (#203, ADR-020): {@code lb://movie-service}/
 * {@code lb://actor-service} resolve via Eureka, and a plain {@code http://}/{@code https://}
 * override (every Kubernetes overlay's shape, Eureka disabled — #228) calls that host directly.
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
 * attached BY HAND to a fresh, private {@link RestClient.Builder} per downstream service, and only
 * when that service's base URL actually uses the {@code lb} scheme (#228). Spring Cloud
 * LoadBalancer's interceptor does not itself check scheme — {@code
 * LoadBalancerInterceptor#intercept} unconditionally treats {@code request.getURI().getHost()} as a
 * service id and asks the load balancer to resolve it, {@code lb://} or not. So a plain {@code
 * http://actor-service:8083} override, attached to the SAME interceptor, still gets load-balanced
 * on "actor-service" as if it were a Eureka service id — which fails with Eureka disabled and no
 * discovery client (confirmed live via {@link
 * dev.lmdb.ai.config.RestClientBaseUrlOverrideIntegrationTest} BEFORE this fix: {@code "No
 * instances available for localhost"}, not a successful call). Skipping the interceptor entirely
 * for a non-{@code lb} scheme is the only way a plain host:port override actually bypasses
 * load-balancer resolution — no {@code ServiceInstance} needs to exist for it at all.
 *
 * <p>With two named {@link RestClient} beans now in the context, each client's constructor
 * disambiguates with an explicit {@code @Qualifier} (ADR-020's consequence note) rather than
 * relying on parameter-name-to-bean-name matching, which needs the {@code -parameters} compiler
 * flag to work and fails silently back to an {@code AmbiguousBeanException} without it.
 */
@Configuration
public class RestClientConfig {

  /** Scheme a base URL must use to go through Eureka-backed load-balancer resolution. */
  private static final String LOAD_BALANCED_SCHEME = "lb";

  /**
   * @param loadBalancerInterceptor resolves {@code lb://} URIs to a real movie-service instance via
   *     Eureka; not attached at all when {@code movieServiceBaseUrl} isn't {@code lb://} (see class
   *     Javadoc)
   * @param movieServiceBaseUrl movie-service's base URL, {@code lb://movie-service} by default
   * @return the {@link RestClient} {@link dev.lmdb.ai.client.MovieCatalogClient} calls
   *     movie-service through — load-balanced on its own, not via the shared builder
   */
  @Bean
  public RestClient movieServiceRestClient(
      BlockingLoadBalancerInterceptor loadBalancerInterceptor,
      @Value("${movie-service.base-url:lb://movie-service}") String movieServiceBaseUrl) {
    return buildRestClient(loadBalancerInterceptor, movieServiceBaseUrl);
  }

  /**
   * @param loadBalancerInterceptor resolves {@code lb://} URIs to a real actor-service instance via
   *     Eureka — the SAME shared interceptor bean {@link #movieServiceRestClient} uses, not a
   *     second one; not attached at all when {@code actorServiceBaseUrl} isn't {@code lb://} (see
   *     class Javadoc)
   * @param actorServiceBaseUrl actor-service's base URL, {@code lb://actor-service} by default
   * @return the {@link RestClient} {@link dev.lmdb.ai.client.ActorCatalogClient} calls
   *     actor-service through — load-balanced on its own, not via the shared builder
   */
  @Bean
  public RestClient actorServiceRestClient(
      BlockingLoadBalancerInterceptor loadBalancerInterceptor,
      @Value("${actor-service.base-url:lb://actor-service}") String actorServiceBaseUrl) {
    return buildRestClient(loadBalancerInterceptor, actorServiceBaseUrl);
  }

  /**
   * Builds one downstream service's {@link RestClient}, attaching {@code loadBalancerInterceptor}
   * only when {@code baseUrl} needs Eureka to resolve it.
   *
   * @param loadBalancerInterceptor the shared load-balancer interceptor
   * @param baseUrl the downstream service's configured base URL
   * @return a {@link RestClient} that either load-balances through Eureka ({@code lb://}) or calls
   *     {@code baseUrl}'s host:port directly (anything else — a plain override, as every k8s
   *     overlay sets once Eureka is disabled)
   */
  private static RestClient buildRestClient(
      BlockingLoadBalancerInterceptor loadBalancerInterceptor, String baseUrl) {
    RestClient.Builder builder = RestClient.builder().baseUrl(baseUrl);
    if (isLoadBalanced(baseUrl)) {
      builder.requestInterceptor(loadBalancerInterceptor);
    }
    return builder.build();
  }

  /**
   * Package-private (not {@code private}) so {@code RestClientConfigTest} can exercise the scheme
   * comparison directly — the exact-match, case-insensitive rule matters (see class Javadoc) and
   * neither {@link dev.lmdb.ai.config.RestClientBaseUrlOverrideIntegrationTest} nor {@link
   * dev.lmdb.ai.integration.AiServiceIntegrationTest} varies the scheme enough to pin it down: both
   * only ever exercise a lowercase {@code lb} or a plain {@code http}, so a {@code contains("lb")}
   * substring match or a case-sensitive {@code equals} would pass either integration test
   * unnoticed.
   *
   * @param baseUrl a downstream service's configured base URL
   * @return {@code true} if {@code baseUrl} uses the {@code lb} scheme and therefore needs
   *     Eureka-backed load-balancer resolution to turn into a real host:port
   */
  static boolean isLoadBalanced(String baseUrl) {
    String scheme = URI.create(baseUrl).getScheme();
    return LOAD_BALANCED_SCHEME.equalsIgnoreCase(scheme);
  }
}
