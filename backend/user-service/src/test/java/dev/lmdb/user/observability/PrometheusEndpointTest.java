package dev.lmdb.user.observability;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Verifies this service's Prometheus scrape surface (issue #23).
 *
 * <p>Guards a failure mode this repo already hit: {@code application.yml} listed {@code prometheus}
 * under the actuator exposure list, which looks complete on inspection, but no module carried
 * {@code micrometer-registry-prometheus}. Without a registry on the classpath Boot never creates
 * the endpoint, so the scrape URL 404s and Prometheus silently records nothing while the config
 * appears correct. Config alone is therefore not evidence — only a real request is.
 *
 * <p>Boots against a Testcontainers PostgreSQL, matching this module's existing integration tests.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
@DisplayName("Prometheus Endpoint Tests")
class PrometheusEndpointTest {

  /** Real PostgreSQL 17; @ServiceConnection wires the datasource. */
  @Container @ServiceConnection
  static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

  /**
   * The name metrics are expected to be tagged with. Read from configuration rather than hardcoded,
   * because a test profile may rename the application (the gateway's does) — the contract is
   * "tagged with THIS service's name", not with one particular literal.
   */
  @Value("${spring.application.name}")
  private String applicationName;

  @Autowired private MockMvc mockMvc;

  /**
   * The endpoint must exist and speak the OpenMetrics text format Prometheus scrapes. Asserting on
   * a JVM metric rather than merely on HTTP 200 proves a registry is actually publishing samples —
   * an exposed-but-empty endpoint would satisfy a status-only assertion while giving Grafana
   * nothing.
   */
  @Test
  @DisplayName("/actuator/prometheus serves scrapeable metrics")
  void prometheusEndpointServesMetrics() throws Exception {
    mockMvc
        .perform(get("/actuator/prometheus"))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("jvm_memory_used_bytes")));
  }

  /**
   * Every sample must carry {@code application=<this service's name>}.
   *
   * <p>Prometheus scrapes all services into one store, so metric names collide across them — {@code
   * http_server_requests_seconds_count} means nothing without knowing which service emitted it. The
   * common {@code management.metrics.tags.application} tag is what lets a shared Grafana dashboard
   * split by service, so it is worth pinning rather than leaving to config review.
   */
  @Test
  @DisplayName("Metrics are tagged with the application name")
  void metricsCarryApplicationTag() throws Exception {
    mockMvc
        .perform(get("/actuator/prometheus"))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("application=\"" + applicationName + "\"")));
  }

  /**
   * Kubernetes routes traffic on readiness and restarts on liveness, so these two paths are
   * load-bearing the moment the service is deployed — a missing {@code
   * management.endpoint.health.probes.enabled} leaves both 404ing and the pod never becomes ready.
   */
  @Test
  @DisplayName("Kubernetes liveness and readiness probes respond")
  void healthProbesRespond() throws Exception {
    mockMvc.perform(get("/actuator/health/liveness")).andExpect(status().isOk());
    mockMvc.perform(get("/actuator/health/readiness")).andExpect(status().isOk());
  }
}
