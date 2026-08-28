package dev.lmdb.ai.config;

import java.time.Duration;
import org.springframework.boot.restclient.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

/**
 * Configures the ambient RestClient.Builder that Spring AI uses for Ollama to enforce a strict read
 * timeout, as spring.ai.ollama.chat.options.timeout is ignored by the autoconfiguration.
 */
@Configuration
public class OllamaConfig {

  @Bean
  public RestClientCustomizer ollamaTimeoutCustomizer() {
    return builder -> {
      SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
      factory.setReadTimeout((int) Duration.ofSeconds(60).toMillis());
      factory.setConnectTimeout((int) Duration.ofSeconds(10).toMillis());
      builder.requestFactory(factory);
    };
  }
}
