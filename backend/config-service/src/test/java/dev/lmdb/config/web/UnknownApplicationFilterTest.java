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
   * {@code /{application}/{profile}}. This filter must recognise "health" as an actually-exposed
   * actuator endpoint ID and let it through rather than treating "actuator" as an unrecognised
   * application.
   */
  @Test
  @DisplayName("An exposed actuator endpoint is served")
  void exposedActuatorEndpointIsServed() {
    ResponseEntity<String> response = get("/actuator/health");

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
  }

  /**
   * {@code env} is deliberately left off {@code management.endpoints.web.exposure.include}, so
   * Boot's actuator has no mapping for it. Without this filter treating "actuator" as anything
   * other than a blanket bypass, that unmapped request would fall through to Spring Cloud Config's
   * generic {@code /{application}/{profile}} controller and be served — an unauthenticated back
   * door to the common configuration documents that bypasses {@code config.security.enabled}
   * entirely. This must 404 the same as any other unrecognised application.
   */
  @Test
  @DisplayName("A non-exposed actuator endpoint 404s rather than falling through to config-server")
  void nonExposedActuatorEndpointReturns404() {
    ResponseEntity<String> response = get("/actuator/env");

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }

  /** A bare, single-segment path (no profile) is not an application request and passes through. */
  @Test
  @DisplayName("A single-segment path is left alone")
  void singleSegmentPathIsNotChecked() {
    ResponseEntity<String> response = get("/actuator");

    assertThat(response.getStatusCode()).isNotEqualTo(HttpStatus.NOT_FOUND);
  }

  /**
   * The three-segment {@code /{application}/{profile}/{label}} shape must be checked the same way
   * as the two-segment one — the upper bound of the segment-count check is real code ({@code
   * segments.length > 3}) that only the {@link #knownApplicationStillServed} and {@link
   * #unknownApplicationReturns404} tests, both two-segment, would never exercise.
   */
  @Test
  @DisplayName("An unrecognised application still 404s with a label segment")
  void unknownApplicationWithLabelReturns404() {
    ResponseEntity<String> response = get("/totally-unknown-app/default/main");

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }

  /** The matching known application must still work with a label segment. */
  @Test
  @DisplayName("A known application with a label segment is still served")
  void knownApplicationWithLabelStillServed() {
    ResponseEntity<String> response = get("/movie-service/default/main");

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
  }

  /**
   * {@code config.server.known-applications} is matched case-sensitively, the same as the URL path
   * segment it's compared against. Documented here rather than left implicit, so a future change to
   * case-insensitive matching is a deliberate choice, not an accident.
   */
  @Test
  @DisplayName("Application name matching is case-sensitive")
  void applicationNameMatchIsCaseSensitive() {
    ResponseEntity<String> response = get("/Movie-Service/default");

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }
}
