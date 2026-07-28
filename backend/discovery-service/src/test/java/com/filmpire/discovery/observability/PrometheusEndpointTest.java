package com.filmpire.discovery.observability;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies this service's Prometheus scrape surface (issue #23).
 *
 * <p>Guards a failure mode this repo already hit: {@code application.yml}
 * listed {@code prometheus} under the actuator exposure list, which looks
 * complete on inspection, but no module carried
 * {@code micrometer-registry-prometheus}. Without a registry on the classpath
 * Boot never creates the endpoint, so the scrape URL 404s and Prometheus
 * silently records nothing while the config appears correct. Config alone is
 * therefore not evidence — only a real request is.</p>
 *
 * <p>Uses {@link TestRestTemplate} against a RANDOM_PORT context rather than
 * MockMvc: this module does not carry the webmvc test-autoconfigure artifact,
 * and the pattern here matches the module's existing integration tests.</p>
 */
@AutoConfigureTestRestTemplate
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("Prometheus Endpoint Tests")
class PrometheusEndpointTest {

    /** Dynamically assigned port for the test server. */
    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    /**
     * The name metrics are expected to be tagged with. Read from configuration
     * rather than hardcoded, because a test profile may rename the application
     * — the contract is "tagged with THIS service's name", not one literal.
     */
    @Value("${spring.application.name}")
    private String applicationName;

    /**
     * Builds an absolute URL against the randomly assigned server port.
     *
     * @param path actuator path to call, e.g. {@code /actuator/prometheus}
     * @return the response, body included, without throwing on 4xx/5xx
     */
    private ResponseEntity<String> get(String path) {
        return restTemplate.getForEntity("http://localhost:" + port + path, String.class);
    }

    /**
     * The endpoint must exist and speak the OpenMetrics text format Prometheus
     * scrapes. Asserting on a JVM metric rather than merely on HTTP 200 proves
     * a registry is actually publishing samples — an exposed-but-empty endpoint
     * would satisfy a status-only assertion while giving Grafana nothing.
     */
    @Test
    @DisplayName("/actuator/prometheus serves scrapeable metrics")
    void prometheusEndpointServesMetrics() {
        ResponseEntity<String> response = get("/actuator/prometheus");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("jvm_memory_used_bytes");
    }

    /**
     * Every sample must carry {@code application=<this service's name>}.
     *
     * <p>Prometheus scrapes all services into one store, so metric names
     * collide across them — {@code jvm_memory_used_bytes} means nothing
     * without knowing which service emitted it. The common
     * {@code management.metrics.tags.application} tag is what lets a shared
     * Grafana dashboard split by service.</p>
     */
    @Test
    @DisplayName("Metrics are tagged with the application name")
    void metricsCarryApplicationTag() {
        ResponseEntity<String> response = get("/actuator/prometheus");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("application=\"" + applicationName + "\"");
    }

    /**
     * Kubernetes routes traffic on readiness and restarts on liveness, so these
     * two paths are load-bearing the moment the service is deployed — a missing
     * {@code management.endpoint.health.probes.enabled} leaves both 404ing and
     * the pod never becomes ready.
     */
    @Test
    @DisplayName("Kubernetes liveness and readiness probes respond")
    void healthProbesRespond() {
        assertThat(get("/actuator/health/liveness").getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(get("/actuator/health/readiness").getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}
