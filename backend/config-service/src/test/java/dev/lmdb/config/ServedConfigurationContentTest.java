package dev.lmdb.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * Checks the values the Config Server actually hands to a client.
 *
 * <p>Boots the real server on a random port with {@code @SpringBootTest} and calls it over HTTP
 * with {@link TestRestTemplate}. The response is read as a map so assertions run against parsed
 * property values, not against text found somewhere in the JSON.
 *
 * <p>Reading the parsed values matters here. The server repeats the requested application name and
 * profile back in its own response, so a substring check on the raw body can pass while the server
 * serves no configuration at all.
 *
 * @see ConfigServerIntegrationTest for the endpoint-reachability tests
 */
@AutoConfigureTestRestTemplate
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {"spring.profiles.active=native,test"})
@DisplayName("Served Configuration Content")
class ServedConfigurationContentTest {

  /** Port assigned to the test server. */
  @LocalServerPort private int port;

  @Autowired private TestRestTemplate restTemplate;

  /**
   * Fetches one application/profile pair and flattens every property source into a single map.
   *
   * <p>Later sources are lower priority in the Config Server response, so the first value seen for
   * a key wins — the same order a client applies.
   *
   * @param application application name, e.g. {@code user-service}
   * @param profile profile name, e.g. {@code default}
   * @return every served property, highest priority first
   */
  private Map<String, Object> fetchProperties(String application, String profile) {
    // 1. Ask the server the same way a config client would.
    String url = "http://localhost:" + port + "/" + application + "/" + profile;
    ResponseEntity<Map<String, Object>> response =
        restTemplate.exchange(
            url, HttpMethod.GET, null, new ParameterizedTypeReference<Map<String, Object>>() {});
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

    // 2. Flatten the property sources, keeping the server's precedence order.
    Map<String, Object> flattened = new LinkedHashMap<>();
    List<Map<String, Object>> sources = readPropertySources(response.getBody());
    for (Map<String, Object> source : sources) {
      @SuppressWarnings("unchecked")
      Map<String, Object> values = (Map<String, Object>) source.get("source");
      values.forEach(flattened::putIfAbsent);
    }
    return flattened;
  }

  /**
   * Pulls the {@code propertySources} list out of a Config Server response.
   *
   * @param body parsed response body, may be {@code null}
   * @return the property sources, never {@code null}
   */
  @SuppressWarnings("unchecked")
  private List<Map<String, Object>> readPropertySources(Map<String, Object> body) {
    assertThat(body).as("config server response body").isNotNull();
    Object sources = body.get("propertySources");
    assertThat(sources).as("propertySources in the response").isInstanceOf(List.class);
    return (List<Map<String, Object>>) sources;
  }

  /**
   * The server must return the configuration file itself, not just an empty envelope.
   *
   * <p>An empty {@code propertySources} list still comes back as HTTP 200 with a non-null body. So
   * a wrong search location, or a renamed file, looks identical to success unless the served values
   * are inspected.
   */
  @Test
  @DisplayName("user-service config is served with its own properties")
  void servesUserServiceProperties() {
    // Given / When
    Map<String, Object> properties = fetchProperties("user-service", "default");

    // Then — values that only exist in user-service.yml
    assertThat(properties).containsEntry("spring.application.name", "user-service");
    assertThat(properties).containsEntry("user.password.min-length", 8);
  }

  /**
   * Secrets must leave the server as placeholders, never as values.
   *
   * <p>The client resolves {@code ${JWT_SECRET}} from its own environment. If the server ever
   * resolved it first, the secret would travel over this HTTP response instead of staying on the
   * machine that owns it.
   */
  @Test
  @DisplayName("Secret placeholders are served unresolved")
  void servesSecretPlaceholdersUnresolved() {
    // Given / When
    Map<String, Object> properties = fetchProperties("user-service", "default");

    // Then — the literal placeholder, not a value
    assertThat(properties).containsEntry("jwt.secret", "${JWT_SECRET}");
    assertThat(properties).containsEntry("spring.datasource.password", "${POSTGRES_PASSWORD}");
  }

  /**
   * The dev and prod profiles must produce genuinely different values.
   *
   * <p>Both are asserted in one test on purpose. A test that only ever requests one profile passes
   * even when the two files are identical, or when one of them is missing.
   */
  @Test
  @DisplayName("dev and prod profiles serve different values")
  void devAndProdDiffer() {
    // Given
    Map<String, Object> dev = fetchProperties("application", "dev");
    Map<String, Object> prod = fetchProperties("application", "prod");

    // Then — same key, deliberately different value per environment
    assertThat(dev).containsEntry("logging.level.root", "DEBUG");
    assertThat(prod).containsEntry("logging.level.root", "WARN");
    assertThat(dev).containsEntry("management.endpoint.health.show-details", "always");
    assertThat(prod).containsEntry("management.endpoint.health.show-details", "never");
  }

  /**
   * The gateway's route table must not be served.
   *
   * <p>Values from the config server take precedence over a service's own {@code application.yml}.
   * The gateway maintains its routes in its own file, so a second copy here would quietly replace
   * the real ones — including its circuit breakers and rate limits — with whatever this file said.
   */
  @Test
  @DisplayName("api-gateway config does not carry a route table")
  void doesNotServeGatewayRouteTable() {
    // Given / When
    Map<String, Object> properties = fetchProperties("api-gateway", "default");

    // Then — shared settings are served
    assertThat(properties).containsEntry("spring.application.name", "api-gateway");
    assertThat(properties).containsEntry("gateway.rate-limit.default-limit", 100);

    // But nothing that would override the gateway's own routing
    assertThat(properties.keySet())
        .noneMatch(key -> key.startsWith("spring.cloud.gateway.server.webflux.routes"));
    assertThat(properties.keySet())
        .noneMatch(key -> key.startsWith("spring.cloud.gateway.server.webflux.discovery"));
  }

  /**
   * movie-service reads Mongo and Redis under specific keys, and both profile documents must put
   * them there.
   *
   * <p>Spring Boot 4 names these two differently: Mongo dropped the {@code data} segment, Redis
   * kept it. A value written one level too high, or under the other spelling, binds to nothing and
   * the service silently falls back to its defaults.
   *
   * <p>dev and prod are both checked. prod is the profile the deployed stack runs on, and the two
   * documents are maintained separately, so testing only one leaves the other free to drift.
   *
   * @param profile the movie-service profile document under test
   */
  @ParameterizedTest(name = "movie-service/{0}")
  @ValueSource(strings = {"dev", "prod"})
  @DisplayName("movie-service profiles nest Mongo and Redis under spring")
  void servesMovieServiceDatastoreKeys(String profile) {
    // Given / When
    Map<String, Object> properties = fetchProperties("movie-service", profile);

    // Then — exactly the keys movie-service binds
    assertThat(properties).containsKey("spring.mongodb.uri");
    assertThat(properties).containsKey("spring.data.redis.host");
    assertThat(properties).containsKey("spring.data.redis.password");

    // And not at the document root, where nothing would read them
    assertThat(properties).doesNotContainKey("mongodb.uri");
    assertThat(properties).doesNotContainKey("redis.host");
  }
}
