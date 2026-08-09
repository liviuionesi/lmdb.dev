package com.filmpire.gateway.contract;

import static org.assertj.core.api.Assertions.assertThat;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Date;
import java.util.List;
import java.util.Map;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.cloud.contract.stubrunner.spring.AutoConfigureStubRunner;
import org.springframework.cloud.contract.stubrunner.spring.StubRunnerProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Consumer-side Spring Cloud Contract StubRunner test for the api-gateway -&gt; ai-service
 * boundary (ADR-008, Task #43).
 *
 * <p>Uses StubRunner in LOCAL mode to consume the generated stub JAR published by ai-service.
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

  private static final String TEST_SECRET =
      "test-secret-key-for-jwt-token-validation-must-be-long-enough-for-tests";

  @Container
  @SuppressWarnings("resource")
  static GenericContainer<?> redis =
      new GenericContainer<>(DockerImageName.parse("redis:7.4-alpine")).withExposedPorts(6379);

  @LocalServerPort private int port;

  private WebTestClient client;

  /**
   * Registers dynamic properties for Redis container host/port and points ai-service gateway route to StubRunner.
   *
   * @param registry dynamic property registry
   */
  @DynamicPropertySource
  static void redisProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.data.redis.host", redis::getHost);
    registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    registry.add("ai-service.uri", () -> "http://localhost:9976");
  }

  /**
   * Initializes {@link WebTestClient} bound to the randomly assigned local server port.
   */
  @BeforeEach
  void setUp() {
    client =
        WebTestClient.bindToServer()
            .baseUrl("http://localhost:" + port)
            .responseTimeout(Duration.ofSeconds(15))
            .build();
  }

  private String createTestToken() {
    SecretKey key = Keys.hmacShaKeyFor(TEST_SECRET.getBytes(StandardCharsets.UTF_8));
    return Jwts.builder()
        .subject("liviu")
        .claim("userId", "123e4567-e89b-12d3-a456-426614174000")
        .claim("roles", List.of("USER"))
        .expiration(new Date(System.currentTimeMillis() + 60000))
        .signWith(key)
        .compact();
  }

  /**
   * Verifies that the gateway routes POST /api/v1/ai/recommendations to the running StubRunner mock server.
   */
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
        .header(HttpHeaders.AUTHORIZATION, "Bearer " + createTestToken())
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
