package dev.lmdb.movie.performance;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static org.assertj.core.api.Assertions.assertThat;

import dev.lmdb.movie.support.AbstractMongoIntegrationTest;
import dev.lmdb.movie.support.TestCacheConfig;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import io.gatling.app.Gatling$;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Executes Gatling performance simulation against Movie Service facade endpoints with WireMock
 * (Task #45).
 *
 * <p>WireMock simulates TMDB with injected latency (250ms fixed delay) to measure TMDB-fallback
 * reads versus local cache-served reads. The generated Gatling HTML report is saved in {@code
 * docs/reports/gatling/}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestCacheConfig.class)
@ActiveProfiles("test")
@DisplayName("Gatling Performance Load Test")
class GatlingPerformanceTest extends AbstractMongoIntegrationTest {

  private static WireMockServer wireMockServer;

  @LocalServerPort private int port;

  @DynamicPropertySource
  static void overrideProperties(DynamicPropertyRegistry registry) {
    registry.add("tmdb.api.base-url", () -> "http://localhost:" + wireMockServer.port());
    registry.add("tmdb.api.key", () -> "test-api-key");
  }

  @BeforeAll
  static void startWireMock() {
    wireMockServer = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
    wireMockServer.start();

    // Stub 1: Fast response for detail 550
    wireMockServer.stubFor(
        get(urlPathMatching("/movie/550.*"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        """
                        {
                          "id": 550,
                          "title": "Fight Club",
                          "original_title": "Fight Club",
                          "overview": "Overview",
                          "poster_path": "/poster.jpg",
                          "backdrop_path": "/backdrop.jpg",
                          "release_date": "1999-10-15",
                          "vote_average": 8.4,
                          "vote_count": 25000,
                          "genres": [],
                          "runtime": 139,
                          "status": "Released",
                          "budget": 63000000,
                          "revenue": 100853753,
                          "popularity": 450.5,
                          "adult": false,
                          "original_language": "en"
                        }
                        """)));

    // Stub 2: Popular movies list
    wireMockServer.stubFor(
        get(urlPathMatching("/movie/popular.*"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        """
                        {
                          "page": 1,
                          "total_pages": 10,
                          "total_results": 200,
                          "results": [
                            {
                              "id": 550,
                              "title": "Fight Club",
                              "overview": "Overview",
                              "poster_path": "/poster.jpg",
                              "backdrop_path": "/backdrop.jpg",
                              "release_date": "1999-10-15",
                              "vote_average": 8.4,
                              "vote_count": 25000,
                              "genre_ids": [18, 53],
                              "popularity": 450.5,
                              "adult": false,
                              "original_language": "en"
                            }
                          ]
                        }
                        """)));

    // Stub 3: Search movies
    wireMockServer.stubFor(
        get(urlPathMatching("/search/movie.*"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        """
                        {
                          "page": 1,
                          "total_pages": 1,
                          "total_results": 1,
                          "results": [
                            {
                              "id": 550,
                              "title": "Fight Club",
                              "overview": "Overview",
                              "poster_path": "/poster.jpg",
                              "backdrop_path": "/backdrop.jpg",
                              "release_date": "1999-10-15",
                              "vote_average": 8.4,
                              "vote_count": 25000,
                              "genre_ids": [18, 53],
                              "popularity": 450.5,
                              "adult": false,
                              "original_language": "en"
                            }
                          ]
                        }
                        """)));

    // Stub 4: TMDB Fallback read for movie 99999 with 250ms injected latency
    wireMockServer.stubFor(
        get(urlPathMatching("/movie/99999.*"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withFixedDelay(250) // Injected TMDB upstream latency
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        """
                        {
                          "id": 99999,
                          "title": "Fallback Movie",
                          "original_title": "Fallback Movie",
                          "overview": "Overview",
                          "poster_path": "/poster.jpg",
                          "backdrop_path": "/backdrop.jpg",
                          "release_date": "2024-01-01",
                          "vote_average": 7.0,
                          "vote_count": 50,
                          "genres": [],
                          "runtime": 100,
                          "status": "Released",
                          "budget": 10000000,
                          "revenue": 20000000,
                          "popularity": 80.0,
                          "adult": false,
                          "original_language": "en"
                        }
                        """)));

    // Stub 5: TMDB Fallback discover query page 99 with 250ms injected latency
    wireMockServer.stubFor(
        get(urlPathMatching("/discover/movie.*"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withFixedDelay(250)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        """
                        {
                          "page": 99,
                          "total_pages": 100,
                          "total_results": 2000,
                          "results": [
                            {
                              "id": 99999,
                              "title": "Fallback Movie",
                              "overview": "Overview",
                              "poster_path": "/poster.jpg",
                              "backdrop_path": "/backdrop.jpg",
                              "release_date": "2024-01-01",
                              "vote_average": 7.0,
                              "vote_count": 50,
                              "genre_ids": [28],
                              "popularity": 80.0,
                              "adult": false,
                              "original_language": "en"
                            }
                          ]
                        }
                        """)));
  }

  @AfterAll
  static void stopWireMock() {
    if (wireMockServer != null) {
      wireMockServer.stop();
    }
  }

  @Test
  @DisplayName("Run Gatling Simulation & Generate Report in docs/reports/gatling")
  void runGatlingSimulation_ShouldGenerateReport() throws Exception {
    System.setProperty("gatling.baseUrl", "http://localhost:" + port);
    Path reportsDir = Paths.get("../../docs/reports/gatling").toAbsolutePath().normalize();
    if (!Files.exists(reportsDir)) {
      Files.createDirectories(reportsDir);
    }

    String[] gatlingArgs =
        new String[] {
          "-s",
          "dev.lmdb.movie.performance.MovieFacadeGatlingSimulation",
          "-rf",
          reportsDir.toString(),
          "-rd",
          "Facade Performance Load Test Run"
        };

    // Gatling.main(String[]) calls System.exit() internally, which would kill this test JVM
    // before the assertion below runs. Gatling$.fromArgs(String[]) returns the status code
    // instead of exiting the process.
    int statusCode = Gatling$.MODULE$.fromArgs(gatlingArgs);
    assertThat(statusCode).isZero();

    File[] reportFolders = reportsDir.toFile().listFiles(File::isDirectory);
    assertThat(reportFolders).isNotNull().isNotEmpty();
  }
}
