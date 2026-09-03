package dev.lmdb.ai.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Unit tests for {@link RestClientConfig#isLoadBalanced}, the scheme check that decides whether a
 * downstream service's {@link org.springframework.web.client.RestClient} goes through Eureka-backed
 * load-balancer resolution (#228). Pure logic, no Spring context — {@link
 * RestClientBaseUrlOverrideIntegrationTest} and {@link
 * dev.lmdb.ai.integration.AiServiceIntegrationTest} both prove the end-to-end wiring works, but
 * neither varies the scheme enough to catch a subtly-wrong comparison (a {@code contains} substring
 * match, or a case-sensitive one) — this class exists specifically to pin that comparison down,
 * independent of the two schemes those integration tests happen to use.
 */
@DisplayName("RestClientConfig.isLoadBalanced (scheme detection)")
class RestClientConfigTest {

  /**
   * Given a base URL, when {@code isLoadBalanced} checks its scheme, then it returns {@code true}
   * only for an exact, case-insensitive {@code lb} scheme — never for a scheme that merely contains
   * "lb" as a substring (the {@code contains} trap a careless rewrite could introduce, e.g. {@code
   * lbx://} or {@code glb://}), and never for the plain {@code http}/{@code https} schemes every
   * k8s overlay's Eureka-disabled override actually uses.
   *
   * @param baseUrl the base URL under test
   * @param expected whether it should be treated as needing load-balancer resolution
   */
  @ParameterizedTest(name = "\"{0}\" -> loadBalanced={1}")
  @CsvSource({
    "lb://actor-service, true",
    "LB://actor-service, true", // mixed/upper case — URI#getScheme() preserves input casing
    "Lb://actor-service, true",
    "http://actor-service:8083, false",
    "https://actor-service:8083, false",
    "lbx://actor-service, false", // contains "lb" as a substring but isn't the "lb" scheme
    "glb://actor-service, false"
  })
  @DisplayName("matches the lb scheme exactly and case-insensitively, nothing else")
  void isLoadBalancedMatchesOnlyTheLbScheme(String baseUrl, boolean expected) {
    assertThat(RestClientConfig.isLoadBalanced(baseUrl)).isEqualTo(expected);
  }
}
