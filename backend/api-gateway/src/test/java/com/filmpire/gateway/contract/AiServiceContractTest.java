package com.filmpire.gateway.contract;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.cloud.contract.stubrunner.spring.AutoConfigureStubRunner;
import org.springframework.cloud.contract.stubrunner.spring.StubRunnerProperties;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Consumer-side Spring Cloud Contract StubRunner test for the api-gateway -> ai-service
 * boundary (ADR-008, Task #43).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("gateway-it")
@AutoConfigureWebTestClient
@Testcontainers
@AutoConfigureStubRunner(
    ids = "com.filmpire:ai-service:+:stubs:9976",
    stubsMode = StubRunnerProperties.StubsMode.LOCAL)
@DisplayName("AI Service Contract Integration Test (#43)")
class AiServiceContractTest {

  @Container
  @SuppressWarnings("resource")
  static GenericContainer<?> redis =
      new GenericContainer<>(DockerImageName.parse("redis:7.4-alpine")).withExposedPorts(6379);

  @LocalServerPort private int port;

  private WebTestClient client;

  @DynamicPropertySource
  static void redisProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.data.redis.host", redis::getHost);
    registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    registry.add("spring.cloud.gateway.routes[5].uri", () -> "http://localhost:9976");
  }

  @BeforeEach
  void setUp() {
    client =
        WebTestClient.bindToServer()
            .baseUrl("http://localhost:" + port)
            .responseTimeout(Duration.ofSeconds(15))
            .build();
  }

  @Test
  @DisplayName("Gateway routes POST /api/v1/ai/recommendations against published ai-service stubs")
  void routesAiRequestToContractStub() {
    Map<String, Object> request =
        Map.of(
            "userId", "123e4567-e89b-12d3-a456-426614174000",
            "recentMovies", List.of("Inception", "Interstellar"),
            "count", 5);

    client
        .post()
        .uri("/api/v1/ai/recommendations")
        .bodyValue(request)
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.recommendations[0].movieId")
        .isEqualTo("550")
        .jsonPath("$.recommendations[0].score")
        .isEqualTo(0.95)
        .jsonPath("$.recommendations[0].reason")
        .value(reason -> assertThat((String) reason).contains("psychological thrillers"));
  }
}
