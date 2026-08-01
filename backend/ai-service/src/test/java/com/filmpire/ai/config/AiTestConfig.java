package com.filmpire.ai.config;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.web.client.RestClient;

import static org.mockito.Mockito.mock;

/**
 * Test-only bean overrides for ai-service's integration tests (#36):
 *
 * <ul>
 *   <li>{@link ChatModel}/{@link EmbeddingModel} become Mockito mocks — no
 *       Ollama server is reachable in CI, and stubbing at this level
 *       (rather than mocking {@code ChatClient} itself) exercises the real
 *       {@code ChatClient} fluent API and Spring AI's structured-output
 *       conversion, same as production. Each test configures the mocks'
 *       behaviour itself.</li>
 *   <li>{@link RestClient.Builder} becomes a plain (non-{@code @LoadBalanced})
 *       builder — {@link com.filmpire.ai.config.RestClientConfig}'s builder
 *       intercepts EVERY request host as a service-id to resolve via
 *       discovery, not just {@code lb://} URIs, so it can't be pointed at
 *       WireMock's plain {@code http://localhost:<port>} the way the real
 *       {@code lb://movie-service} is resolved via Eureka.</li>
 * </ul>
 */
@TestConfiguration
public class AiTestConfig {

    @Bean
    @Primary
    public ChatModel chatModel() {
        return mock(ChatModel.class);
    }

    @Bean
    @Primary
    public EmbeddingModel embeddingModel() {
        return mock(EmbeddingModel.class);
    }

    @Bean
    @Primary
    public RestClient.Builder nonLoadBalancedRestClientBuilder() {
        return RestClient.builder();
    }
}
