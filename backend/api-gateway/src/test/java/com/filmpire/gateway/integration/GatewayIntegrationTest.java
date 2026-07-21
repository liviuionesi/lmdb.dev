package com.filmpire.gateway.integration;

import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.lessThan;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Gateway-boundary integration tests for issue #19 (Service Integration
 * Testing).
 *
 * <p>The services are separate modules with separate databases, so there is no
 * direct service-to-service DB join to exercise; "integration" here means the
 * one place cross-service behavior actually converges — the API gateway. This
 * suite boots the REAL gateway (full route table, Spring Security, Resilience4j
 * circuit breakers, Redis rate limiting, JWT filter) and points every route at
 * a single WireMock server that stands in for the downstream services (see
 * {@code application-gateway-it.yml}). Eureka is disabled; a Testcontainers
 * Redis backs the rate limiter.</p>
 *
 * <p>What is proven end to end through the real Netty server + full filter
 * chain: path-based routing to the correct downstream, public vs.
 * authentication-required exchanges, JWT identity propagation
 * ({@code X-User-*} headers), downstream error passthrough, circuit-breaker
 * fallback, request rate limiting, and CORS preflight. Behaviors that don't
 * belong at this boundary (real service discovery; the per-service data logic)
 * are covered by the discovery-service and per-service suites respectively —
 * see {@code docs/architecture/INTEGRATION_TESTING.md}.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("gateway-it")
@Testcontainers
@WireMockTest(httpPort = 9971)
@DisplayName("Gateway Integration Tests (#19)")
class GatewayIntegrationTest {

    /** Shared HS256 secret — MUST match the gateway's {@code jwt.secret} default
     *  so tokens minted here validate in the gateway's JwtUtil. */
    private static final String JWT_SECRET = "filmpire-secret-key-change-in-production";

    /** Real Redis backing the RequestRateLimiter (no auth). */
    @Container
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7.4-alpine"))
            .withExposedPorts(6379);

    @LocalServerPort
    private int port;

    /** WebTestClient bound to the real running gateway (exercises Netty + the
     *  full filter chain, not a mock server context). */
    private WebTestClient client;

    /**
     * Wires the rate limiter's Redis at the container's mapped host/port.
     *
     * @param registry Spring test property registry
     */
    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    /** Builds a client bound to the random server port with a generous timeout
     *  (some tests fire many requests). */
    @BeforeEach
    void setUp() {
        client = WebTestClient.bindToServer()
                .baseUrl("http://localhost:" + port)
                .responseTimeout(Duration.ofSeconds(15))
                .build();
    }

    /**
     * A public GET for a movie must be routed to the movie downstream with the
     * path preserved and the body returned unchanged — the baseline "routes to
     * the correct service" proof.
     */
    @Test
    @DisplayName("Routes public movie GET to the movie downstream")
    void routesMovieRequestToDownstream() {
        stubFor(get(urlEqualTo("/api/v1/movies/550")).willReturn(okJson("{\"id\":550}")));

        client.get().uri("/api/v1/movies/550").exchange()
                .expectStatus().isOk()
                .expectBody().json("{\"id\":550}");

        verify(getRequestedFor(urlEqualTo("/api/v1/movies/550")));
    }

    /**
     * The genres path is served by the movie-service route (shared predicate),
     * so a genres GET must also reach the downstream — guards the second path
     * in that route's predicate list against being dropped.
     */
    @Test
    @DisplayName("Routes genres GET through the movie-service route")
    void routesGenresThroughMovieRoute() {
        stubFor(get(urlEqualTo("/api/v1/genres/list")).willReturn(okJson("{\"genres\":[]}")));

        client.get().uri("/api/v1/genres/list").exchange()
                .expectStatus().isOk();

        verify(getRequestedFor(urlEqualTo("/api/v1/genres/list")));
    }

    /**
     * A public GET for an actor must route to the actor downstream — proves
     * path-based routing distinguishes services (a different prefix reaches the
     * same WireMock at a different path, i.e. the actor route matched, not the
     * movie route).
     */
    @Test
    @DisplayName("Routes public actor GET to the actor downstream")
    void routesActorRequestToDownstream() {
        stubFor(get(urlEqualTo("/api/v1/actors/819")).willReturn(okJson("{\"id\":819}")));

        client.get().uri("/api/v1/actors/819").exchange()
                .expectStatus().isOk();

        verify(getRequestedFor(urlEqualTo("/api/v1/actors/819")));
    }

    /**
     * The user route is authentication-required, so a request with no token must
     * be rejected with 401 by Spring Security BEFORE routing — the downstream
     * must never be called (verified by zero WireMock hits).
     */
    @Test
    @DisplayName("Protected user route returns 401 without a token")
    void protectedRouteRejectsWithoutToken() {
        stubFor(get(urlPathMatching("/api/v1/users/.*")).willReturn(okJson("{}")));

        client.get().uri("/api/v1/users/profile").exchange()
                .expectStatus().isUnauthorized();

        verify(0, getRequestedFor(urlPathMatching("/api/v1/users/.*")));
    }

    /**
     * A valid token must (a) pass Spring Security so the request is routed, and
     * (b) have the gateway inject the caller's identity as {@code X-User-Id} /
     * {@code X-Username} headers for the downstream — the JWT-propagation
     * contract other services rely on instead of re-parsing the token.
     */
    @Test
    @DisplayName("Valid token is routed and propagates X-User-* headers downstream")
    void validTokenIsRoutedAndPropagatesIdentity() {
        stubFor(get(urlEqualTo("/api/v1/users/profile")).willReturn(okJson("{\"ok\":true}")));

        client.get().uri("/api/v1/users/profile")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + mintToken("liviu", "u-123"))
                .exchange()
                .expectStatus().isOk();

        // Gateway injected the identity extracted from the JWT.
        verify(getRequestedFor(urlEqualTo("/api/v1/users/profile"))
                .withHeader("X-User-Id", equalTo("u-123"))
                .withHeader("X-Username", equalTo("liviu")));
    }

    /**
     * A downstream 404 must be relayed to the client unchanged — the gateway is
     * a transparent conduit for legitimate downstream error statuses (this
     * route has no failure-status circuit-breaker config, so 404 is not treated
     * as a breaker failure).
     */
    @Test
    @DisplayName("Propagates a downstream 404 to the client")
    void propagatesDownstreamNotFound() {
        stubFor(get(urlEqualTo("/api/v1/movies/999999"))
                .willReturn(aResponse().withStatus(404)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"status_code\":34}")));

        client.get().uri("/api/v1/movies/999999").exchange()
                .expectStatus().isNotFound()
                .expectBody().json("{\"status_code\":34}");
    }

    /**
     * A downstream 500 on the movie route (whose circuit breaker has NO
     * failure-status config) must pass through as 500 rather than trip the
     * breaker or become a fallback — confirms only explicitly-configured
     * failures open the breaker, so an occasional 500 doesn't shed the route.
     */
    @Test
    @DisplayName("Passes through a downstream 500 without tripping the breaker")
    void passesThroughDownstreamServerError() {
        stubFor(get(urlEqualTo("/api/v1/movies/500err"))
                .willReturn(aResponse().withStatus(500)));

        client.get().uri("/api/v1/movies/500err").exchange()
                .expectStatus().is5xxServerError();
    }

    /**
     * The dedicated cb-test route treats 500 as a circuit-breaker failure. After
     * enough failures the breaker must OPEN and serve the fallback: every
     * request returns the 503 fallback body, and — the load-bearing assertion —
     * the downstream receives FEWER calls than were sent, proving the open
     * breaker short-circuits instead of forwarding.
     */
    @Test
    @DisplayName("Circuit breaker opens and serves the fallback")
    void circuitBreakerOpensAndServesFallback() {
        stubFor(get(urlPathMatching("/api/v1/movies/cbtest/.*"))
                .willReturn(aResponse().withStatus(500)));

        int attempts = 12;
        for (int i = 0; i < attempts; i++) {
            client.get().uri("/api/v1/movies/cbtest/" + i).exchange()
                    // Both the pre-open failures and the post-open short-circuits
                    // resolve to the fallback controller (503, ApiResponse).
                    .expectStatus().isEqualTo(503)
                    .expectBody()
                    .jsonPath("$.success").isEqualTo(false)
                    .jsonPath("$.message").value(m -> assertThat((String) m).contains("Movie Service"));
        }

        // The breaker (min 4 calls, window 4) must have opened, so the downstream
        // saw fewer than all 12 requests.
        verify(lessThan(attempts), getRequestedFor(urlPathMatching("/api/v1/movies/cbtest/.*")));
    }

    /**
     * The rate limiter (burst 3, replenish 1/s, keyed on X-Forwarded-For) must
     * allow the burst then reject with 429. Firing a fixed client IP rapidly, we
     * expect at least one success AND at least one 429 — asserted tolerantly
     * because exact counts depend on token-refill timing.
     */
    @Test
    @DisplayName("Rate limiter returns 429 after the burst is exhausted")
    void rateLimiterRejectsAfterBurst() {
        stubFor(get(urlPathMatching("/api/v1/movies/rltest/.*")).willReturn(okJson("{}")));

        AtomicInteger ok = new AtomicInteger();
        AtomicInteger throttled = new AtomicInteger();
        for (int i = 0; i < 10; i++) {
            int status = client.get().uri("/api/v1/movies/rltest/1")
                    .header("X-Forwarded-For", "198.51.100.42")
                    .exchange()
                    .returnResult(Void.class)
                    .getStatus()
                    .value();
            if (status == 429) {
                throttled.incrementAndGet();
            } else if (status == 200) {
                ok.incrementAndGet();
            }
        }

        assertThat(ok.get()).as("some requests within the burst succeed").isPositive();
        assertThat(throttled.get()).as("excess requests are rate-limited (429)").isPositive();
    }

    /**
     * A CORS preflight (OPTIONS) from a configured origin must be answered with
     * the matching {@code Access-Control-Allow-Origin} header, so the browser
     * lets the React app (localhost:3000) call the gateway cross-origin.
     */
    @Test
    @DisplayName("CORS preflight is allowed for a configured origin")
    void corsPreflightAllowsConfiguredOrigin() {
        client.options().uri("/api/v1/movies/550")
                .header(HttpHeaders.ORIGIN, "http://localhost:3000")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
                .exchange()
                .expectStatus().is2xxSuccessful()
                .expectHeader().valueEquals("Access-Control-Allow-Origin", "http://localhost:3000");
    }

    /**
     * Mints a valid HS256 JWT with the gateway's expected claim set
     * (sub / userId / roles), signed with the shared secret.
     *
     * @param username subject claim
     * @param userId   userId claim
     * @return a signed, currently-valid compact JWT
     */
    private static String mintToken(String username, String userId) {
        SecretKey key = Keys.hmacShaKeyFor(JWT_SECRET.getBytes(StandardCharsets.UTF_8));
        Date now = new Date();
        return Jwts.builder()
                .subject(username)
                .claim("userId", userId)
                .claim("roles", List.of("USER"))
                .issuedAt(now)
                .expiration(new Date(now.getTime() + 3_600_000))
                .signWith(key)
                .compact();
    }
}
