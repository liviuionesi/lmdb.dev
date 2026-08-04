package com.filmpire.ai.config;

import static org.mockito.Mockito.mock;

import com.filmpire.ai.service.SpeechToTextService;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Test-only Mockito mocks for the model-backed collaborators ({@link ChatModel}, {@link
 * EmbeddingModel}, {@link SpeechToTextService}) that no CI environment can reach for real (no
 * Ollama, no Vosk model). Split out from {@link AiTestConfig} so tests that need these mocks but
 * want the real {@code @LoadBalanced} {@link org.springframework.web.client.RestClient.Builder}
 * from {@link RestClientConfig} — e.g. a load-balancer resolution regression test — can import
 * just this class instead of {@link AiTestConfig}'s bundle, which also overrides the builder.
 */
@TestConfiguration
public class AiModelTestConfig {

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
   * @return a Mockito mock standing in for the real Vosk-backed {@link SpeechToTextService} — no
   *     model is downloaded in CI (#68), and this exercises the controller's multipart/HTTP
   *     contract, not Vosk itself (that's {@link com.filmpire.ai.service.SpeechToTextServiceTest}'s
   *     job)
   */
  @Bean
  @Primary
  public SpeechToTextService speechToTextService() {
    return mock(SpeechToTextService.class);
  }
}
