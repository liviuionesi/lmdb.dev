package dev.lmdb.config.security;

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
 * Verifies the access-control-enabled state of {@link ConfigServerSecurityConfig} (#244): with
 * {@code config.security.enabled=true}, config endpoints require the configured HTTP Basic
 * credential, while actuator health/readiness/liveness/Prometheus stay open — a deployment must be
 * able to turn this on without losing its own monitoring.
 *
 * @see ConfigServerAccessControlDisabledTest for the default state
 */
@AutoConfigureTestRestTemplate
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "spring.profiles.active=native,test",
      "config.security.enabled=true",
      "config.security.username=test-config-user",
      "config.security.password=test-config-password"
    })
@DisplayName("Config Server Access Control — enabled")
class ConfigServerAccessControlEnabledTest {

  private static final String USERNAME = "test-config-user";
  private static final String PASSWORD = "test-config-password";

  @LocalServerPort private int port;

  /** Plain client, no credentials attached — used to prove the endpoint is actually gated. */
  @Autowired private TestRestTemplate restTemplate;

  private String url(String path) {
    return "http://localhost:" + port + path;
  }

  /** A config endpoint request carrying no credential must be rejected, not silently served. */
  @Test
  @DisplayName("Config endpoint rejects a request with no credential")
  void configEndpointRejectsAnonymousRequest() {
    ResponseEntity<String> response =
        restTemplate.getForEntity(url("/movie-service/default"), String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  /**
   * An unrecognised application must still 404, not 401, once access control is enabled. {@link
   * dev.lmdb.config.web.UnknownApplicationFilter} is registered ahead of the security chain
   * specifically so this holds regardless of credentials — a client probing application names
   * shouldn't learn anything by whether it's authenticated.
   *
   * <p>This guards a real regression this feature shipped with once: the filter originally called
   * {@code HttpServletResponse.sendError()}, which Tomcat turns into an internal forward to Boot's
   * {@code /error} handler. That forward re-enters the filter chain on the {@code ERROR} dispatch
   * type, which Spring Security's chain also covers by default, so the 404 was silently replaced
   * with a 401 the moment access control was turned on.
   */
  @Test
  @DisplayName("Unknown application 404s regardless of credentials")
  void unknownApplicationReturns404NotUnauthorized() {
    ResponseEntity<String> anonymous =
        restTemplate.getForEntity(url("/totally-unknown-app/default"), String.class);
    ResponseEntity<String> authenticated =
        restTemplate
            .withBasicAuth(USERNAME, PASSWORD)
            .getForEntity(url("/totally-unknown-app/default"), String.class);

    assertThat(anonymous.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(authenticated.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }

  /** The wrong password must also be rejected — not merely "any credential passes". */
  @Test
  @DisplayName("Config endpoint rejects the wrong password")
  void configEndpointRejectsWrongPassword() {
    ResponseEntity<String> response =
        restTemplate
            .withBasicAuth(USERNAME, "not-the-password")
            .getForEntity(url("/movie-service/default"), String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  /** The configured credential must be accepted and the config actually served. */
  @Test
  @DisplayName("Config endpoint serves the configuration with the correct credential")
  void configEndpointServesWithCorrectCredential() {
    ResponseEntity<String> response =
        restTemplate
            .withBasicAuth(USERNAME, PASSWORD)
            .getForEntity(url("/movie-service/default"), String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).contains("movie-service");
  }

  /**
   * Kubernetes/compose health checks and Prometheus don't carry a config-server credential — if
   * these needed one, the deployment would show unhealthy the moment access control is turned on.
   */
  @Test
  @DisplayName("Actuator health, readiness, liveness and Prometheus stay open")
  void actuatorEndpointsStayOpen() {
    assertThat(restTemplate.getForEntity(url("/actuator/health"), String.class).getStatusCode())
        .isEqualTo(HttpStatus.OK);
    assertThat(
            restTemplate
                .getForEntity(url("/actuator/health/readiness"), String.class)
                .getStatusCode())
        .isEqualTo(HttpStatus.OK);
    assertThat(
            restTemplate
                .getForEntity(url("/actuator/health/liveness"), String.class)
                .getStatusCode())
        .isEqualTo(HttpStatus.OK);
    assertThat(restTemplate.getForEntity(url("/actuator/prometheus"), String.class).getStatusCode())
        .isEqualTo(HttpStatus.OK);
  }

  /**
   * {@code /actuator/**} is permitted regardless of credentials, including a sub-path actuator
   * itself doesn't expose (e.g. {@code env}, left off {@code
   * management.endpoints.web.exposure.include}) — that falls through to Spring Cloud Config's own
   * {@code /{application}/{profile}} controller, which treats "actuator" as just another
   * unrecognised-but-permitted application name and answers 200 with the common {@code
   * application.yml} properties. That response is harmless (it's the same file every real client
   * already receives), but it must never carry {@link #PASSWORD} — this is the actual risk the
   * blanket actuator exemption creates, not any particular status code.
   */
  @Test
  @DisplayName("A non-exposed actuator sub-path never leaks the config-server credential")
  void actuatorFallthroughNeverLeaksCredential() {
    ResponseEntity<String> response = restTemplate.getForEntity(url("/actuator/env"), String.class);

    assertThat(response.getBody()).doesNotContain(PASSWORD);
  }
}
