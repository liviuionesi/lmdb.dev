package com.filmpire.gateway.observability;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * Verifies this service's Prometheus scrape surface (issue #23).
 *
 * <p>Guards a failure mode this repo already hit: {@code application.yml} listed {@code prometheus}
 * under the actuator exposure list, which looks complete on inspection, but no module carried
 * {@code micrometer-registry-prometheus}. Without a registry on the classpath Boot never creates
 * the endpoint, so the scrape URL 404s and Prometheus silently records nothing while the config
 * appears correct. Config alone is therefore not evidence — only a real request is.
 *
 * <p>Uses {@link WebTestClient} because the gateway is reactive (WebFlux); the servlet MockMvc used
 * by the other services has no context here.
 */
@SpringBootTest
@AutoConfigureWebTestClient
@ActiveProfiles("test")
@DisplayName("Prometheus Endpoint Tests")
class PrometheusEndpointTest {

  /**
   * The name metrics are expected to be tagged with. Read from configuration rather than hardcoded,
   * because a test profile may rename the application (the gateway's does) — the contract is
   * "tagged with THIS service's name", not with one particular literal.
   */
  @Value("${spring.application.name}")
  private String applicationName;

  @Autowired private WebTestClient webTestClient;

  /**
   * The endpoint must exist and speak the OpenMetrics text format Prometheus scrapes. Asserting on
   * a JVM metric rather than merely on HTTP 200 proves a registry is actually publishing samples —
   * an exposed-but-empty endpoint would satisfy a status-only assertion while giving Grafana
   * nothing.
   */
  @Test
  @DisplayName("/actuator/prometheus serves scrapeable metrics")
  void prometheusEndpointServesMetrics() {
    webTestClient
        .get()
        .uri("/actuator/prometheus")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody(String.class)
        .value(body -> assertThat(body).contains("jvm_memory_used_bytes"));
  }

  /**
   * Every sample must carry {@code application=<this service's name>}.
   *
   * <p>Prometheus scrapes all services into one store, so metric names collide across them. The
   * gateway matters most here: its {@code spring_cloud_gateway_*} series are the only place
   * per-route latency is visible, and untagged they cannot be attributed.
   */
  @Test
  @DisplayName("Metrics are tagged with the application name")
  void metricsCarryApplicationTag() {
    webTestClient
        .get()
        .uri("/actuator/prometheus")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody(String.class)
        .value(body -> assertThat(body).contains("application=\"" + applicationName + "\""));
  }

  /**
   * Kubernetes routes traffic on readiness and restarts on liveness, so these two paths are
   * load-bearing the moment the service is deployed — a missing {@code
   * management.endpoint.health.probes.enabled} leaves both 404ing and the pod never becomes ready.
   */
  @Test
  @DisplayName("Kubernetes liveness and readiness probes respond")
  void healthProbesRespond() {
    webTestClient.get().uri("/actuator/health/liveness").exchange().expectStatus().isOk();
    webTestClient.get().uri("/actuator/health/readiness").exchange().expectStatus().isOk();
  }
}
