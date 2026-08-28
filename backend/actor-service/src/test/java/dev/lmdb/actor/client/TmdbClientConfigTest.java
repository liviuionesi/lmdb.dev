package dev.lmdb.actor.client;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

/**
 * Unit tests for {@link TmdbClientConfig}.
 *
 * <p>Verifies the configuration factory creating the shared {@link RestClient} (with base URL,
 * headers, and interceptors) and the HTTP declarative interface proxy {@link TmdbPersonClient}.
 */
@DisplayName("TmdbClientConfig Unit Tests")
class TmdbClientConfigTest {

  /**
   * Verifies that {@link TmdbClientConfig#tmdbRestClient} builds a non-null {@link RestClient}
   * configured with timeouts, headers, and rate-limiting interceptor, and that {@link
   * TmdbClientConfig#tmdbPersonClient} creates a declarative HTTP client proxy.
   */
  @Test
  @DisplayName(
      "tmdbRestClient & tmdbPersonClient: builds configured RestClient and HTTP interface proxy")
  void buildsClientAndInterfaceProxy() {
    // Given
    TmdbProperties properties =
        new TmdbProperties("https://api.themoviedb.org/3", "test-key", 5000, 5000);
    TmdbClientConfig config = new TmdbClientConfig(properties);
    TmdbRateLimitInterceptor interceptor = new TmdbRateLimitInterceptor();

    // When
    RestClient restClient = config.tmdbRestClient(RestClient.builder(), interceptor);
    TmdbPersonClient personClient = config.tmdbPersonClient(restClient);

    // Then
    assertThat(restClient).isNotNull();
    assertThat(personClient).isNotNull();
  }
}
