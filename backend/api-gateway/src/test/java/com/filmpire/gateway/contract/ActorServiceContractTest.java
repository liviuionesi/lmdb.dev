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
 * Consumer-side Spring Cloud Contract StubRunner test for the api-gateway -&gt; actor-service
 * boundary (ADR-008, Task #43).
 *
 * <p>Uses StubRunner in LOCAL mode to consume the generated stub JAR published by actor-service.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("gateway-it")
@AutoConfigureWebTestClient
@Testcontainers
@AutoConfigureStubRunner(
    ids = "com.filmpire:actor-service:+:stubs:9974",
    stubsMode = StubRunnerProperties.StubsMode.LOCAL)
@DisplayName("Actor Service Contract Integration Test (#43)")
class ActorServiceContractTest {

  @Container
  @SuppressWarnings("resource")
  static GenericContainer<?> redis =
      new GenericContainer<>(DockerImageName.parse("redis:7.4-alpine")).withExposedPorts(6379);

  @LocalServerPort private int port;

  private WebTestClient client;

  /**
   * Registers dynamic properties for Redis container host/port and points actor-service gateway route to StubRunner.
   *
   * @param registry dynamic property registry
   */
  @DynamicPropertySource
  static void redisProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.data.redis.host", redis::getHost);
    registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    registry.add("spring.cloud.gateway.routes[4].uri", () -> "http://localhost:9974");
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

  /**
   * Verifies that the gateway routes GET /api/v1/actors/819 to the running StubRunner mock server.
   */
  @Test
  @DisplayName("Gateway routes GET /api/v1/actors/819 against published actor-service stubs")
  void routesActorRequestToContractStub() {
    client
        .get()
        .uri("/api/v1/actors/819")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.data.tmdbId")
        .isEqualTo(819)
        .jsonPath("$.data.name")
        .isEqualTo("Edward Norton")
        .jsonPath("$.data.biography")
        .value(bio -> assertThat((String) bio).contains("Edward Harrison Norton"));
  }
}
