package com.filmpire.ai.config;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Regression test for the {@code lb://movie-service} resolution bug fixed in {@link
 * RestClientConfig}: {@link com.filmpire.ai.client.MovieCatalogClient} must resolve {@code
 * lb://movie-service} through Spring Cloud LoadBalancer for real, not merely reach movie-service
 * when pointed at a plain HTTP URL — which is all {@link AiTestConfig}'s builder override lets the
 * rest of the suite verify (see its Javadoc). Eureka stays disabled (no server in CI); Spring
 * Cloud's {@code SimpleDiscoveryClient} stands in, registering movie-service against WireMock, so
 * the real {@code @LoadBalanced} {@link org.springframework.web.client.RestClient.Builder} has an
 * actual instance to resolve {@code lb://movie-service} to.
 */
@SpringBootTest(
    properties = {
      "spring.cloud.discovery.enabled=true",
      "spring.cloud.discovery.client.simple.instances.movie-service[0].uri=http://localhost:9993"
    })
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
@Import(AiModelTestConfig.class)
@WireMockTest(httpPort = 9993)
@Transactional
@DisplayName("RestClientConfig load-balancing (lb://movie-service resolution)")
class RestClientConfigLoadBalancingTest {

  @Container @ServiceConnection
  static PostgreSQLContainer postgres = new PostgreSQLContainer("pgvector/pgvector:pg17");

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private ChatModel chatModel;
  @Autowired private EmbeddingModel embeddingModel;

  /**
   * Stubs movie-service (via WireMock, registered as a discovery-client instance rather than a
   * hardcoded {@code movie-service.base-url}) to return one candidate, and verifies it comes back
   * in the recommendation response — proving {@code lb://movie-service} resolved through Spring
   * Cloud LoadBalancer's discovery client rather than throwing {@code Unroutable protocol scheme}.
   *
   * @throws Exception if the MockMvc request fails to execute
   */
  @Test
  @DisplayName(
      "POST /api/v1/ai/recommendations resolves lb://movie-service via the discovery client")
  void recommendationsResolveMovieServiceThroughDiscoveryClient() throws Exception {
    stubFor(
        get(urlPathEqualTo("/api/v1/movies/popular"))
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        """
                {"content":[{"tmdbId":550,"title":"Fight Club","overview":"...","voteAverage":8.4}],
                 "pageNumber":0,"pageSize":30,"totalElements":1,"totalPages":1,
                 "first":true,"last":true,"hasNext":false,"hasPrevious":false,"numberOfElements":1}
                """)));
    when(chatModel.getOptions()).thenReturn(ChatOptions.builder().build());
    when(chatModel.call(any(Prompt.class)))
        .thenReturn(
            new ChatResponse(
                List.of(
                    new Generation(
                        new AssistantMessage(
                            """
                    [{"movieId":"550","score":0.9,"reason":"Same tone as Se7en"}]
                    """)))));
    when(embeddingModel.embed(anyString())).thenReturn(new float[768]);

    String body =
        objectMapper.writeValueAsString(
            Map.of(
                "userId", UUID.randomUUID(),
                "recentMovies", List.of("Se7en"),
                "count", 1));

    mockMvc
        .perform(
            post("/api/v1/ai/recommendations")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.recommendations[0].movieId").value("550"));
  }
}
