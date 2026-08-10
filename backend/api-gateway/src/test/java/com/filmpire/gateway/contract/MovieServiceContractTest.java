package com.filmpire.gateway.contract;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
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
 * Consumer-side Spring Cloud Contract StubRunner test for the api-gateway -&gt; movie-service
 * boundary (ADR-008, Task #43).
 *
 * <p>Uses StubRunner in LOCAL mode to consume the generated stub JAR published by movie-service.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("gateway-it")
@AutoConfigureWebTestClient
@Testcontainers
@AutoConfigureStubRunner(
    ids = "com.filmpire:movie-service:+:stubs:9973",
    stubsMode = StubRunnerProperties.StubsMode.LOCAL)
@DisplayName("Movie Service Contract Integration Test (#43)")
class MovieServiceContractTest {

  @Container
  @SuppressWarnings("resource")
  static GenericContainer<?> redis =
      new GenericContainer<>(DockerImageName.parse("redis:7.4-alpine")).withExposedPorts(6379);

  @LocalServerPort private int port;

  private WebTestClient client;

  /**
   * Registers dynamic properties for Redis container host/port and points movie-service gateway
   * route to StubRunner.
   *
   * @param registry dynamic property registry
   */
  @DynamicPropertySource
  static void redisProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.data.redis.host", redis::getHost);
    registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    registry.add("movie-service.uri", () -> "http://localhost:9973");
  }

  /** Initializes {@link WebTestClient} bound to the randomly assigned local server port. */
  @BeforeEach
  void setUp() {
    client =
        WebTestClient.bindToServer()
            .baseUrl("http://localhost:" + port)
            .responseTimeout(Duration.ofSeconds(15))
            .build();
  }

  /**
   * Verifies that the gateway routes GET /api/v1/movies/550 to the running StubRunner mock server.
   */
  @Test
  @DisplayName("Gateway routes GET /api/v1/movies/550 against published movie-service stubs")
  void routesMovieRequestToContractStub() {
    client
        .get()
        .uri("/api/v1/movies/550")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.id")
        .isEqualTo(550)
        .jsonPath("$.title")
        .isEqualTo("Fight Club")
        .jsonPath("$.overview")
        .value(overview -> assertThat((String) overview).contains("insomniac"));
  }
}
