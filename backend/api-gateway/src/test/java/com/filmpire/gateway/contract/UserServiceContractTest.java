package com.filmpire.gateway.contract;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Date;
import java.util.List;
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
 * Consumer-side Spring Cloud Contract StubRunner test for the api-gateway -> user-service
 * boundary (ADR-008, Task #43).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("gateway-it")
@AutoConfigureWebTestClient
@Testcontainers
@AutoConfigureStubRunner(
    ids = "com.filmpire:user-service:+:stubs:9975",
    stubsMode = StubRunnerProperties.StubsMode.LOCAL)
@DisplayName("User Service Contract Integration Test (#43)")
class UserServiceContractTest {

  private static final String TEST_SECRET =
      "test-secret-key-for-jwt-token-validation-must-be-long-enough-for-tests";

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
    registry.add("spring.cloud.gateway.routes[3].uri", () -> "http://localhost:9975");
  }

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

  @Test
  @DisplayName("Gateway routes GET /api/v1/users/profile against published user-service stubs")
  void routesUserRequestToContractStub() {
    client
        .get()
        .uri("/api/v1/users/profile")
        .header(HttpHeaders.AUTHORIZATION, "Bearer " + createTestToken())
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.data.username")
        .isEqualTo("liviu")
        .jsonPath("$.data.email")
        .isEqualTo("liviu@example.com");
  }
}
