package dev.lmdb.config.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Verifies {@link ConfigServerSecurityConfig} stays well-defined when {@code
 * config.security.enabled} holds neither {@code true} nor {@code false} — a typo, e.g. {@code
 * CONFIG_SECURITY_ENABLED=1} or {@code =enabled}, rather than the exact string this feature reads.
 *
 * <p>An earlier version of {@link ConfigServerSecurityConfig} paired
 * {@code @ConditionalOnProperty(havingValue = "true")} with a mirrored {@code havingValue =
 * "false"} bean. A value that was neither matched neither condition, leaving the application
 * context with zero {@link SecurityFilterChain} beans — an undefined state, not a safely-open one.
 * This locks in the fix: exactly one bean exists (and access stays open) for any value that isn't
 * the literal {@code true}.
 */
@AutoConfigureTestRestTemplate
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {"spring.profiles.active=native,test", "config.security.enabled=banana"})
@DisplayName("Config Server Access Control — misconfigured value")
class ConfigServerAccessControlMisconfiguredTest {

  @LocalServerPort private int port;

  @Autowired private TestRestTemplate restTemplate;

  @Autowired private ApplicationContext context;

  /** Exactly one {@link SecurityFilterChain} bean must exist, never zero and never two. */
  @Test
  @DisplayName("Exactly one SecurityFilterChain bean exists")
  void exactlyOneSecurityFilterChainBeanExists() {
    assertThat(context.getBeanNamesForType(SecurityFilterChain.class)).hasSize(1);
  }

  /** A value that isn't the literal {@code true} falls back to this server's open default. */
  @Test
  @DisplayName("Config endpoint stays open, matching the disabled default")
  void configEndpointStaysOpen() {
    ResponseEntity<String> response =
        restTemplate.getForEntity(
            "http://localhost:" + port + "/movie-service/default", String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
  }
}
