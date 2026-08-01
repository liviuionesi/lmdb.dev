package com.filmpire.ai.config;

import com.filmpire.ai.service.SpeechToTextService;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.web.client.RestClient;

import static org.mockito.Mockito.mock;

/**
 * Test-only bean overrides for ai-service's integration tests:
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

    /**
     * @return a Mockito mock standing in for the real Ollama-backed {@link ChatModel}
     */
    @Bean
    @Primary
    public ChatModel chatModel() {
        return mock(ChatModel.class);
    }

    /**
     * @return a Mockito mock standing in for the real Ollama-backed {@link EmbeddingModel}
     */
    @Bean
    @Primary
    public EmbeddingModel embeddingModel() {
        return mock(EmbeddingModel.class);
    }

    /**
     * @return a plain (non-{@code @LoadBalanced}) builder so tests can point
     *         it at WireMock's {@code http://localhost:<port>} directly
     */
    @Bean
    @Primary
    public RestClient.Builder nonLoadBalancedRestClientBuilder() {
        return RestClient.builder();
    }

    /**
     * @return a Mockito mock standing in for the real Vosk-backed {@link SpeechToTextService}
     *         — no model is downloaded in CI (#68), and this exercises the
     *         controller's multipart/HTTP contract, not Vosk itself (that's
     *         {@link com.filmpire.ai.service.SpeechToTextServiceTest}'s job)
     */
    @Bean
    @Primary
    public SpeechToTextService speechToTextService() {
        return mock(SpeechToTextService.class);
    }
}
