package dev.lmdb.movie.client;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import dev.lmdb.movie.client.dto.TmdbMovieListResponse;
import dev.lmdb.movie.client.dto.TmdbMovieResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Integration tests for the declarative {@code @HttpExchange} client {@link TmdbClient} and the
 * blocking Bucket4j rate limiter ({@code TmdbRateLimitInterceptor}) attached to it.
 *
 * <p>Boots the Spring context without a web server ({@code WebEnvironment.NONE}) and replaces the
 * real TMDB API with a WireMock server pinned to port 9999 by {@code @WireMockTest}; the client's
 * base URL is redirected there before the context starts.
 *
 * <p>Maintainer notes: the fixed port means this class must not run concurrently with anything else
 * bound to 9999. Both tests share one Spring context and therefore one token bucket, and the
 * throttling test measures wall-clock time, so it is inherently timing-sensitive.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
@WireMockTest(httpPort = 9999)
@DisplayName("TmdbClient and Rate Limiting Integration Tests")
class TmdbClientIntegrationTest {

  @Autowired private TmdbClient tmdbClient;

  /**
   * Redirects the TMDB base URL to the WireMock port and supplies a dummy API key so no real
   * credentials are needed. {@code bucket4j.enabled=false} turns off the Bucket4j Spring Boot
   * starter's auto-configuration; the client-side interceptor is a plain component and stays
   * active, which is exactly what the throttling test exercises.
   */
  @DynamicPropertySource
  static void configureProperties(DynamicPropertyRegistry registry) {
    registry.add("tmdb.api.base-url", () -> "http://localhost:9999");
    registry.add("tmdb.api.key", () -> "test-api-key");
    registry.add("bucket4j.enabled", () -> "false");
  }

  /**
   * End-to-end happy path through the generated HTTP-interface proxy: proves the URL template, the
   * {@code api_key} query parameter and the JSON-to-record deserialization all line up with what
   * the stubbed TMDB endpoint returns.
   */
  @Test
  @DisplayName("Should fetch movie details successfully")
  void shouldFetchMovieDetails() {
    // Given: WireMock returns a minimal TMDB movie payload for id 550
    stubFor(
        get(urlPathEqualTo("/movie/550"))
            .withQueryParam("api_key", equalTo("test-api-key"))
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        """
                                {
                                  "id": 550,
                                  "title": "Fight Club",
                                  "overview": "An insomniac office worker..."
                                }
                                """)));

    // When: the movie is fetched through the declarative client
    TmdbMovieResponse response = tmdbClient.getMovieDetails(550L, "test-api-key");

    // Then: the payload is deserialized into the response record
    assertThat(response).isNotNull();
    assertThat(response.id()).isEqualTo(550L);
    assertThat(response.title()).isEqualTo("Fight Club");
  }

  /**
   * Given a year range, when {@code discoverMovies} is called, then the request WireMock actually
   * receives carries TMDB's {@code primary_release_date.gte}/{@code .lte} params (not {@code year})
   * — #218 AC1/AC2, verified at the real HTTP-request layer via WireMock's query-param matcher, not
   * just by inspecting what the client method was called with.
   */
  @Test
  @DisplayName("discoverMovies: a year range sends primary_release_date.gte/.lte, not year")
  void shouldDiscoverMoviesWithYearRange() {
    // Given: WireMock only matches if gte/lte are present and year is absent
    stubFor(
        get(urlPathEqualTo("/discover/movie"))
            .withQueryParam("primary_release_date.gte", equalTo("2000-01-01"))
            .withQueryParam("primary_release_date.lte", equalTo("2010-12-31"))
            .withQueryParam("year", absent())
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        """
                        {"page":1,"total_pages":1,"total_results":1,
                         "results":[{"id":27205,"title":"Inception"}]}
                        """)));

    // When
    TmdbMovieListResponse response =
        tmdbClient.discoverMovies(
            "test-api-key",
            1,
            "popularity.desc",
            null,
            null,
            "2000-01-01",
            "2010-12-31",
            null,
            null);

    // Then: WireMock only would have matched the stub above if the right params were sent —
    // a non-null response here IS the proof, not just an assertion on its content.
    assertThat(response).isNotNull();
    assertThat(response.results()).extracting("id").containsExactly(27205L);
  }

  /**
   * Given only the pre-existing exact {@code year} filter (no range), when {@code discoverMovies}
   * is called, then the request sent still carries {@code year} and NOT the new {@code
   * primary_release_date.gte}/{@code .lte} params — #218 AC3's regression guarantee, proven at the
   * real HTTP-request layer rather than assumed from the range test alone.
   */
  @Test
  @DisplayName("discoverMovies: exact year alone sends year, not primary_release_date bounds")
  void shouldDiscoverMoviesWithExactYearOnlyUnchanged() {
    // Given: WireMock only matches if year is present and gte/lte are absent
    stubFor(
        get(urlPathEqualTo("/discover/movie"))
            .withQueryParam("year", equalTo("2024"))
            .withQueryParam("primary_release_date.gte", absent())
            .withQueryParam("primary_release_date.lte", absent())
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        """
                        {"page":1,"total_pages":1,"total_results":1,
                         "results":[{"id":550,"title":"Fight Club"}]}
                        """)));

    // When
    TmdbMovieListResponse response =
        tmdbClient.discoverMovies(
            "test-api-key", 1, "popularity.desc", null, 2024, null, null, null, null);

    // Then
    assertThat(response).isNotNull();
    assertThat(response.results()).extracting("id").containsExactly(550L);
  }

  /**
   * Burns through more requests than the bucket's 40-token capacity: if the blocking interceptor
   * were missing, all 42 calls against local WireMock would finish in a few milliseconds, so any
   * measurable delay proves requests actually wait for tokens. The loose 200ms floor keeps the test
   * stable on machines of varying speed.
   */
  @Test
  @DisplayName("Rate Limiter should throttle requests above 40 per 10s")
  void shouldThrottleRequestsWhenExceedingLimit() {
    // Given: a stubbed endpoint that responds instantly
    stubFor(
        get(urlPathEqualTo("/movie/13"))
            .withQueryParam("api_key", equalTo("test-api-key"))
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withBody("{\"id\": 13, \"title\": \"Forrest Gump\"}")));

    Instant start = Instant.now();

    // Fire 42 synchronous requests. The first 40 should be instant.
    // The last 2 should block and delay execution, proving Bucket4J is working.
    IntStream.range(0, 42)
        .forEach(
            i -> {
              tmdbClient.getMovieDetails(13L, "test-api-key");
            });

    Duration executionTime = Duration.between(start, Instant.now());

    // Since the limit is 40 per 10s, going over 40 must force at least some blocking
    // A single token trickles in every 250ms (10000ms / 40).
    // Request 41 adds ~250ms, Request 42 adds ~250ms -> >500ms guaranteed delay
    assertThat(executionTime.toMillis()).isGreaterThan(200);
  }
}
