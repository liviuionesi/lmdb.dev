package dev.lmdb.config.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * Verifies {@link UnknownApplicationFilter} (#244): a config-server {@code {application}} this
 * server has no file for must 404, not answer with an empty-but-200 environment.
 *
 * <p>Boots the real server on a random port with {@code @SpringBootTest} and calls it over HTTP
 * with {@link TestRestTemplate}, the same style as {@link
 * dev.lmdb.config.ConfigServerIntegrationTest}.
 */
@AutoConfigureTestRestTemplate
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {"spring.profiles.active=native,test"})
@DisplayName("Unknown Application Filter")
class UnknownApplicationFilterTest {

  @LocalServerPort private int port;

  @Autowired private TestRestTemplate restTemplate;

  private ResponseEntity<String> get(String path) {
    return restTemplate.getForEntity("http://localhost:" + port + path, String.class);
  }

  /**
   * An application name absent from {@code config.server.known-applications} must 404. Without this
   * filter, Spring Cloud Config's default behavior is to answer 200 with whatever common properties
   * happen to match — indistinguishable from a real, empty configuration.
   */
  @Test
  @DisplayName("An unrecognised application 404s instead of serving an empty config")
  void unknownApplicationReturns404() {
    ResponseEntity<String> response = get("/totally-unknown-app/default");

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }

  /** The same check must not break a real, known application. */
  @Test
  @DisplayName("A known application is still served")
  void knownApplicationStillServed() {
    ResponseEntity<String> response = get("/movie-service/default");

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
  }

  /**
   * {@code /actuator/health} also has two path segments ("actuator", "health"), the same shape as
   * {@code /{application}/{profile}}. Without an explicit exemption, this filter would treat
   * "actuator" as an unrecognised application and 404 every actuator endpoint.
   */
  @Test
  @DisplayName("Actuator paths are exempt from the application check")
  void actuatorPathIsExempt() {
    ResponseEntity<String> response = get("/actuator/health");

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
  }

  /** A bare, single-segment path (no profile) is not an application request and passes through. */
  @Test
  @DisplayName("A single-segment path is left alone")
  void singleSegmentPathIsNotChecked() {
    ResponseEntity<String> response = get("/actuator");

    assertThat(response.getStatusCode()).isNotEqualTo(HttpStatus.NOT_FOUND);
  }
}
