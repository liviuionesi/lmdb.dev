package dev.lmdb.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * Checks that the Config Server's HTTP endpoints are reachable.
 *
 * <p>Boots the real server on a random port with {@code @SpringBootTest} and calls it with {@link
 * TestRestTemplate}. These tests cover reachability only. The values the server returns are
 * asserted in {@link ServedConfigurationContentTest}, because a reachable endpoint and a correct
 * payload are two different things.
 *
 * @see ServedConfigurationContentTest
 */
@AutoConfigureTestRestTemplate
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {"spring.profiles.active=native,test"})
class ConfigServerIntegrationTest {

  private static final String LOCALHOST_URL_PREFIX = "http://localhost:";
  private static final String ACTUATOR_HEALTH_PATH = "/actuator/health";
  private static final String ACTUATOR_INFO_PATH = "/actuator/info";

  /** Dynamically assigned port for the test server. */
  @LocalServerPort private int port;

  /**
   * REST template for making HTTP requests to the test server. Note: @Autowired is required for
   * TestRestTemplate in Spring Boot test context.
   */
  @Autowired private TestRestTemplate restTemplate;

  /**
   * Verifies that the actuator health endpoint is accessible and reports correct status.
   *
   * <p>This test ensures that:
   *
   * <ul>
   *   <li>The /actuator/health endpoint is accessible
   *   <li>The response status is HTTP 200 OK
   *   <li>The health status is "UP" indicating the service is healthy
   * </ul>
   *
   * <p>The health endpoint is critical for monitoring and orchestration tools (e.g., Kubernetes
   * liveness/readiness probes, load balancers).
   */
  @Test
  void actuatorHealthEndpointIsAccessible() {
    String url = LOCALHOST_URL_PREFIX + port + ACTUATOR_HEALTH_PATH;
    ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    // The top-level status, not just any UP in the body: a DOWN aggregate can
    // still contain an UP component.
    assertThat(response.getBody()).contains("\"status\":\"UP\"");
  }

  /**
   * Verifies that the actuator info endpoint is accessible.
   *
   * <p>This test ensures that:
   *
   * <ul>
   *   <li>The /actuator/info endpoint is accessible
   *   <li>The response status is HTTP 200 OK
   * </ul>
   *
   * <p>The info endpoint provides application metadata such as version, name, and description,
   * useful for operations and monitoring.
   */
  @Test
  void actuatorInfoEndpointIsAccessible() {
    String url = LOCALHOST_URL_PREFIX + port + ACTUATOR_INFO_PATH;
    ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
  }

  /**
   * Verifies that the Config Server can serve default application configuration.
   *
   * <p>This test ensures that:
   *
   * <ul>
   *   <li>The Config Server is properly initialized
   *   <li>Configuration files can be retrieved
   *   <li>The default profile configuration is accessible
   * </ul>
   *
   * <p>This verifies the core Config Server functionality of serving configuration to client
   * applications.
   */
  @Test
  void canRetrieveDefaultConfiguration() {
    String url = LOCALHOST_URL_PREFIX + port + "/application/default";
    ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isNotNull();
  }

  /**
   * Verifies that the Config Server can serve service-specific configuration.
   *
   * <p>This test ensures that:
   *
   * <ul>
   *   <li>A service-specific path is routed and answered
   * </ul>
   *
   * <p>Only reachability. Whether the returned values are the right ones is asserted in {@link
   * ServedConfigurationContentTest}.
   */
  @Test
  void canRetrieveServiceSpecificConfiguration() {
    String url = LOCALHOST_URL_PREFIX + port + "/movie-service/default";
    ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody()).contains("movie-service");
  }

  /**
   * Verifies that the Config Server can serve environment-specific configuration.
   *
   * <p>This test ensures that:
   *
   * <ul>
   *   <li>A profile-specific path is routed and answered
   * </ul>
   *
   * <p>That dev and prod actually differ is asserted in {@link ServedConfigurationContentTest}.
   */
  @Test
  void canRetrieveEnvironmentSpecificConfiguration() {
    String url = LOCALHOST_URL_PREFIX + port + "/application/dev";
    ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isNotNull();
  }
}
