package com.filmpire.ai.config;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.web.client.RestClient;

/**
 * Test-only bean overrides for ai-service's integration tests:
 *
 * <ul>
 *   <li>{@link AiModelTestConfig}'s {@code ChatModel}/{@code EmbeddingModel}/{@code
 *       SpeechToTextService} mocks — no Ollama or Vosk is reachable in CI.
 *   <li>{@link RestClient.Builder} becomes a plain (non-{@code @LoadBalanced}) builder — {@link
 *       com.filmpire.ai.config.RestClientConfig}'s builder resolves any request's host as a
 *       service-id via Spring Cloud LoadBalancer, so it can't be pointed at WireMock's plain {@code
 *       http://localhost:<port>} the way the real {@code lb://movie-service} is resolved via
 *       Eureka. Tests that need the real load-balanced builder (e.g. a {@code lb://} resolution
 *       regression test) should import {@link AiModelTestConfig} directly instead of this class.
 * </ul>
 */
@TestConfiguration
@Import(AiModelTestConfig.class)
public class AiTestConfig {

  /**
   * @return a plain (non-{@code @LoadBalanced}) builder so tests can point it at WireMock's {@code
   *     http://localhost:<port>} directly
   */
  @Bean
  @Primary
  public RestClient.Builder nonLoadBalancedRestClientBuilder() {
    return RestClient.builder();
  }
}
