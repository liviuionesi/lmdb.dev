package dev.lmdb.actor.client;

import java.time.Duration;
import org.jspecify.annotations.NonNull;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * HTTP client configuration for the TMDB API.
 *
 * <p>Defines one shared, rate-limited {@link RestClient} and builds {@link TmdbPersonClient} on top
 * of it — the single typed HTTP interface used by both the native {@code /api/v1/actors} API and
 * the TMDB v3 facade ({@code dev.lmdb.actor.facade}) as of ADR-010, mirroring movie-service's
 * client config.
 *
 * <p>The TMDB API key is injected as a default query parameter on every outbound request via a
 * {@link org.springframework.http.client.ClientHttpRequestInterceptor}, so business-layer code
 * never handles or even sees the key — it is a pure cross-cutting concern of the HTTP transport.
 */
@Configuration
public class TmdbClientConfig {

  private final TmdbProperties tmdbProperties;

  /**
   * @param tmdbProperties type-safe TMDB configuration
   */
  public TmdbClientConfig(TmdbProperties tmdbProperties) {
    this.tmdbProperties = tmdbProperties;
  }

  /**
   * The shared HTTP client for all TMDB traffic from this service: base URL, JSON headers, the
   * rate-limit interceptor, the API-key interceptor, and connect/read timeouts.
   *
   * @param builder Spring-provided builder
   * @param rateLimitInterceptor bucket-based throttle (40 req / 10 s)
   * @return shared TMDB RestClient
   */
  @Bean
  public RestClient tmdbRestClient(
      RestClient.Builder builder, @NonNull TmdbRateLimitInterceptor rateLimitInterceptor) {
    // Configure a request factory with explicit connect and read timeouts
    // so a hung TMDB connection cannot block a Tomcat thread indefinitely.
    org.springframework.http.client.SimpleClientHttpRequestFactory requestFactory =
        new org.springframework.http.client.SimpleClientHttpRequestFactory();
    requestFactory.setConnectTimeout(Duration.ofMillis(tmdbProperties.connectTimeout()));
    requestFactory.setReadTimeout(Duration.ofMillis(tmdbProperties.readTimeout()));

    // Build RestClient with baseUrl, standard JSON accept/content-type headers,
    // rate-limit interceptor, and a transparent API-key interceptor that appends
    // the server-side TMDB key as a query parameter on every outbound request.
    return builder
        .baseUrl(tmdbProperties.baseUrl())
        .defaultHeader("Accept", "application/json")
        .defaultHeader("Content-Type", "application/json")
        .requestFactory(requestFactory)
        .requestInterceptor(rateLimitInterceptor)
        .requestInterceptor(
            (request, body, execution) -> {
              // Append the server-side api_key to every TMDB request. This keeps the
              // key out of business code — callers never pass or know about it.
              var enrichedUri =
                  UriComponentsBuilder.fromUri(request.getURI())
                      .queryParam("api_key", tmdbProperties.key())
                      .build(true)
                      .toUri();
              return execution.execute(
                  new org.springframework.http.client.support.HttpRequestWrapper(request) {
                    @Override
                    public java.net.URI getURI() {
                      return enrichedUri;
                    }
                  },
                  body);
            })
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
    // Adapt RestClient for declarative HTTP interface and build factory.
    RestClientAdapter adapter = RestClientAdapter.create(tmdbRestClient);
    HttpServiceProxyFactory factory = HttpServiceProxyFactory.builderFor(adapter).build();

    // Instantiate the proxy implementation for TmdbPersonClient interface.
    return factory.createClient(TmdbPersonClient.class);
  }
}
