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
 * Verifies the default, access-control-disabled state of {@link ConfigServerSecurityConfig} (#244):
 * with {@code config.security.enabled} left at its default of {@code false}, config endpoints
 * answer without any credential — the behavior this server had before access control existed, and
 * what local development relies on.
 *
 * @see ConfigServerAccessControlEnabledTest for the opposite state
 */
@AutoConfigureTestRestTemplate
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {"spring.profiles.active=native,test", "config.security.enabled=false"})
@DisplayName("Config Server Access Control — disabled (default)")
class ConfigServerAccessControlDisabledTest {

  @LocalServerPort private int port;

  @Autowired private TestRestTemplate restTemplate;

  private ResponseEntity<String> get(String path) {
    return restTemplate.getForEntity("http://localhost:" + port + path, String.class);
  }

  /** No credential is required for a config endpoint when access control is off. */
  @Test
  @DisplayName("Config endpoint is served without a credential")
  void configEndpointNeedsNoCredential() {
    ResponseEntity<String> response = get("/movie-service/default");

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
  }

  /** Actuator health must also stay reachable in this state. */
  @Test
  @DisplayName("Actuator health is served without a credential")
  void actuatorHealthNeedsNoCredential() {
    ResponseEntity<String> response = get("/actuator/health");

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
  }
}
