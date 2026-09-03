package dev.lmdb.ai.config;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import dev.lmdb.ai.client.ActorCatalogClient;
import dev.lmdb.ai.client.CandidateMovie;
import dev.lmdb.ai.client.MovieCatalogClient;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration;
import org.springframework.boot.data.redis.autoconfigure.DataRedisRepositoriesAutoConfiguration;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Regression coverage for #228: with Eureka disabled and no discovery client (exactly the shape
 * every Kubernetes overlay runs in — {@code EUREKA_CLIENT_ENABLED=false}, no {@code
 * SimpleDiscoveryClient} instances), a downstream client whose {@code *-service.base-url} is a
 * plain {@code http://host:port} URL must still reach that host directly, without ever attempting
 * Eureka-backed {@code lb://} resolution. {@link dev.lmdb.ai.integration.AiServiceIntegrationTest}
 * already covers the opposite (and previously only-tested) case — {@code lb://} resolved through a
 * registered {@code SimpleDiscoveryClient} instance — so this class is the one place that actually
 * exercises the config shape the overlays ship, closing the gap #228 found: {@code
 * ACTOR_SERVICE_BASE_URL} was missing from three overlays' {@code ai-service-config}, which nothing
 * caught because nothing ran this scenario.
 *
 * <p>Deliberately boots only {@link RestClientConfig} and the two downstream clients — not the full
 * {@link dev.lmdb.ai.AiServiceApplication} context — via {@link SpringBootTest}'s {@code classes}
 * attribute, with JPA/Flyway/Redis autoconfiguration excluded: this scenario is entirely about
 * {@code RestClient}/load-balancer wiring, and needs neither PostgreSQL nor Redis to prove it.
 */
@SpringBootTest(
    classes = {RestClientConfig.class, ActorCatalogClient.class, MovieCatalogClient.class},
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties = {
      "movie-service.base-url=http://localhost:9993",
      "actor-service.base-url=http://localhost:9993"
    })
@EnableAutoConfiguration(
    exclude = {
      DataSourceAutoConfiguration.class,
      HibernateJpaAutoConfiguration.class,
      FlywayAutoConfiguration.class,
      DataRedisAutoConfiguration.class,
      DataRedisRepositoriesAutoConfiguration.class
    })
@ActiveProfiles("test") // application-test.yml: eureka.client.enabled=false, no discovery client
@WireMockTest(httpPort = 9993)
@DisplayName(
    "RestClientConfig base-url override (Eureka disabled, plain http:// — mirrors the k8s"
        + " overlays)")
class RestClientBaseUrlOverrideIntegrationTest {

  @Autowired private ActorCatalogClient actorCatalogClient;

  @Autowired private MovieCatalogClient movieCatalogClient;

  /**
   * Given {@code actor-service.base-url} is a plain {@code http://} URL (as every overlay's {@code
   * ai-service-config} now sets it, post-#228) and Eureka is disabled, when {@link
   * ActorCatalogClient#findPersonId} is called, then it reaches the configured host directly and
   * returns the stubbed result — proving the override actually bypasses {@code lb://} resolution
   * rather than silently degrading to an empty result the way it did before #228's fix (the
   * overlays' missing key left {@code actor-service.base-url} at its {@code lb://actor-service}
   * default, which {@link
   * org.springframework.cloud.client.loadbalancer.BlockingLoadBalancerInterceptor} cannot resolve
   * with no discovery client registered).
   */
  @Test
  @DisplayName("reaches actor-service through the plain http:// override, not lb://")
  void findPersonIdReachesActorServiceThroughPlainHttpOverride() {
    stubFor(
        get(urlPathEqualTo("/api/v1/actors/search"))
            .willReturn(
                okJson(
                    "{\"success\":true,\"statusCode\":200,\"data\":{\"results\":[{\"tmdbId\":42,"
                        + "\"name\":\"Test Person\"}]}}")));

    Optional<Long> personId = actorCatalogClient.findPersonId("Test Person");

    assertThat(personId).contains(42L);
  }

  /**
   * Same proof as above, for {@link MovieCatalogClient} — verified independently since it is a
   * separate {@link org.springframework.web.client.RestClient} bean in {@link RestClientConfig},
   * and #228's bug was specific to {@code actor-service.base-url} being the one of the two left
   * unset; this confirms the already-correct {@code movie-service.base-url} pattern this fix
   * mirrors still behaves the same way.
   *
   * <p>Given {@code movie-service.base-url} is a plain {@code http://} URL and Eureka is disabled,
   * when {@link MovieCatalogClient#fetchCandidates} is called, then it reaches the configured host
   * directly and returns the stubbed candidates.
   */
  @Test
  @DisplayName("reaches movie-service through the plain http:// override, not lb://")
  void fetchCandidatesReachesMovieServiceThroughPlainHttpOverride() {
    stubFor(
        get(urlPathEqualTo("/api/v1/movies/popular"))
            .willReturn(
                okJson(
                    "{\"content\":[{\"tmdbId\":7,\"title\":\"Test Movie\"}],\"totalElements\":1,"
                        + "\"totalPages\":1,\"pageNumber\":0,\"pageSize\":1,\"first\":true,"
                        + "\"last\":true,\"hasNext\":false,\"hasPrevious\":false,"
                        + "\"numberOfElements\":1}")));

    List<CandidateMovie> candidates = movieCatalogClient.fetchCandidates(1);

    assertThat(candidates).hasSize(1);
    assertThat(candidates.get(0).tmdbId()).isEqualTo(7L);
  }
}
