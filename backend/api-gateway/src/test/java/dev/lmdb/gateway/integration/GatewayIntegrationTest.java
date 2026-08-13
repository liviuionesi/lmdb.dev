package dev.lmdb.gateway.integration;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.absent;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.lessThan;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.EntityExchangeResult;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Gateway-boundary integration tests for issues #19 (Service Integration Testing) and #33 (TMDB v3
 * facade routing + auth/account proxy).
 *
 * <p>The services are separate modules with separate databases, so there is no direct
 * service-to-service DB join to exercise; "integration" here means the one place cross-service
 * behavior actually converges — the API gateway. This suite boots the REAL gateway (full route
 * table, Spring Security, Resilience4j circuit breakers, Redis rate limiting, JWT filter) and
 * points its routes at WireMock servers standing in for the downstreams (see {@code
 * application-gateway-it.yml}). Eureka is disabled; a Testcontainers Redis backs the rate limiter.
 *
 * <p><strong>Two WireMock servers, on purpose.</strong> Port 9971 (the {@code @WireMockTest}
 * instance, reachable via the static {@code WireMock.*} helpers) plays movie-service and — on its
 * {@code /3/**} paths — the real TMDB. Port 9972 ({@link #actorMock}) plays actor-service. Several
 * assertions are about routing reaching the <em>correct</em> downstream, and one shared server
 * cannot prove that: every route would hit the same port, so a person request mis-routed to
 * movie-service would still pass. Splitting the ports makes the destination observable.
 *
 * <p>What is proven end to end through the real Netty server + full filter chain: path-based
 * routing to the correct downstream, public vs. authentication-required exchanges, JWT identity
 * propagation ({@code X-User-*} headers), downstream error passthrough, circuit-breaker fallback,
 * request rate limiting, CORS preflight, and — for #33 — the bare TMDB catalog surface, client
 * {@code api_key} stripping, and the auth/account proxy's key injection, {@code session_id}
 * forwarding and verbatim error passthrough. Behaviors that don't belong at this boundary (real
 * service discovery; the per-service data logic) are covered by the discovery-service and
 * per-service suites respectively — see {@code docs/architecture/INTEGRATION_TESTING.md}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("gateway-it")
@AutoConfigureWebTestClient
@Testcontainers
@WireMockTest(httpPort = 9971)
@DisplayName("Gateway Integration Tests (#19, #33)")
class GatewayIntegrationTest {

  /**
   * Shared HS256 secret — MUST match {@code jwt.secret} in {@code application-gateway-it.yml} so
   * tokens minted here validate in the gateway's JwtUtil. There's no production default to fall
   * back to anymore (#114); this is test-only.
   */
  private static final String JWT_SECRET =
      "test-secret-key-for-jwt-token-validation-must-be-long-enough-for-tests";

  /**
   * Port the actor-service stand-in listens on; matches the {@code actor-service} and {@code
   * tmdb-person-facade} route URIs in {@code application-gateway-it.yml}.
   */
  private static final int ACTOR_MOCK_PORT = 9972;

  /** Real Redis backing the RequestRateLimiter (no auth). */
  @Container
  @SuppressWarnings("resource")
  static GenericContainer<?> redis =
      new GenericContainer<>(DockerImageName.parse("redis:7.4-alpine")).withExposedPorts(6379);

  /**
   * Second stand-in downstream: actor-service. Managed manually because {@code @WireMockTest}
   * configures only one instance per class.
   */
  private static WireMockServer actorMock;

  @LocalServerPort private int port;

  /**
   * WebTestClient bound to the real running gateway (exercises Netty + the full filter chain, not a
   * mock server context).
   */
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

  /** Starts the actor-service stand-in on its fixed port. */
  @BeforeAll
  static void startActorMock() {
    actorMock = new WireMockServer(options().port(ACTOR_MOCK_PORT));
    actorMock.start();
  }

  /** Stops the actor-service stand-in so the port is free for the next class. */
  @AfterAll
  static void stopActorMock() {
    if (actorMock != null) {
      actorMock.stop();
    }
  }

  /**
   * Builds a client bound to the random server port with a generous timeout (some tests fire many
   * requests), and clears the manually-managed actor mock — {@code @WireMockTest} resets the 9971
   * instance itself, but 9972 would otherwise leak stubs and request counts across tests.
   */
  @BeforeEach
  void setUp() {
    client =
        WebTestClient.bindToServer()
            .baseUrl("http://localhost:" + port)
            .responseTimeout(Duration.ofSeconds(15))
            .build();
    actorMock.resetAll();
  }

  /**
   * A public GET for a movie must be routed to the movie downstream with the path preserved and the
   * body returned unchanged — the baseline "routes to the correct service" proof.
   */
  @Test
  @DisplayName("Routes public movie GET to the movie downstream")
  void routesMovieRequestToDownstream() {
    stubFor(get(urlEqualTo("/api/v1/movies/550")).willReturn(okJson("{\"id\":550}")));

    client
        .get()
        .uri("/api/v1/movies/550")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .json("{\"id\":550}");

    verify(getRequestedFor(urlEqualTo("/api/v1/movies/550")));
  }

  /**
   * The genres path is served by the movie-service route (shared predicate), so a genres GET must
   * also reach the downstream — guards the second path in that route's predicate list against being
   * dropped.
   */
  @Test
  @DisplayName("Routes genres GET through the movie-service route")
  void routesGenresThroughMovieRoute() {
    stubFor(get(urlEqualTo("/api/v1/genres/list")).willReturn(okJson("{\"genres\":[]}")));

    client.get().uri("/api/v1/genres/list").exchange().expectStatus().isOk();

    verify(getRequestedFor(urlEqualTo("/api/v1/genres/list")));
  }

  /**
   * A public GET for an actor must route to the ACTOR downstream (port 9972), not the movie one —
   * with separate stand-in servers this genuinely proves path-based routing picks the right service
   * rather than merely proving the path survived the gateway.
   */
  @Test
  @DisplayName("Routes public actor GET to the actor downstream")
  void routesActorRequestToDownstream() {
    actorMock.stubFor(get(urlEqualTo("/api/v1/actors/819")).willReturn(okJson("{\"id\":819}")));

    client.get().uri("/api/v1/actors/819").exchange().expectStatus().isOk();

    actorMock.verify(getRequestedFor(urlEqualTo("/api/v1/actors/819")));
  }

  /**
   * The user route is authentication-required, so a request with no token must be rejected with 401
   * by Spring Security BEFORE routing — the downstream must never be called (verified by zero
   * WireMock hits).
   */
  @Test
  @DisplayName("Protected user route returns 401 without a token")
  void protectedRouteRejectsWithoutToken() {
    stubFor(get(urlPathMatching("/api/v1/users/.*")).willReturn(okJson("{}")));

    client.get().uri("/api/v1/users/profile").exchange().expectStatus().isUnauthorized();

    verify(0, getRequestedFor(urlPathMatching("/api/v1/users/.*")));
  }

  /**
   * A valid token must (a) pass Spring Security so the request is routed, and (b) have the gateway
   * inject the caller's identity as {@code X-User-Id} / {@code X-Username} headers for the
   * downstream — the JWT-propagation contract other services rely on instead of re-parsing the
   * token.
   */
  @Test
  @DisplayName("Valid token is routed and propagates X-User-* headers downstream")
  void validTokenIsRoutedAndPropagatesIdentity() {
    stubFor(get(urlEqualTo("/api/v1/users/profile")).willReturn(okJson("{\"ok\":true}")));

    client
        .get()
        .uri("/api/v1/users/profile")
        .header(HttpHeaders.AUTHORIZATION, "Bearer " + mintToken("liviu", "u-123"))
        .exchange()
        .expectStatus()
        .isOk();

    // Gateway injected the identity extracted from the JWT.
    verify(
        getRequestedFor(urlEqualTo("/api/v1/users/profile"))
            .withHeader("X-User-Id", equalTo("u-123"))
            .withHeader("X-Username", equalTo("liviu")));
  }

  /**
   * A downstream 404 must be relayed to the client unchanged — the gateway is a transparent conduit
   * for legitimate downstream error statuses (this route has no failure-status circuit-breaker
   * config, so 404 is not treated as a breaker failure).
   */
  @Test
  @DisplayName("Propagates a downstream 404 to the client")
  void propagatesDownstreamNotFound() {
    stubFor(
        get(urlEqualTo("/api/v1/movies/999999"))
            .willReturn(
                aResponse()
                    .withStatus(404)
                    .withHeader("Content-Type", "application/json")
                    .withBody("{\"status_code\":34}")));

    client
        .get()
        .uri("/api/v1/movies/999999")
        .exchange()
        .expectStatus()
        .isNotFound()
        .expectBody()
        .json("{\"status_code\":34}");
  }

  /**
   * A downstream 500 on the movie route (whose circuit breaker has NO failure-status config) must
   * pass through as 500 rather than trip the breaker or become a fallback — confirms only
   * explicitly-configured failures open the breaker, so an occasional 500 doesn't shed the route.
   */
  @Test
  @DisplayName("Passes through a downstream 500 without tripping the breaker")
  void passesThroughDownstreamServerError() {
    stubFor(get(urlEqualTo("/api/v1/movies/500err")).willReturn(aResponse().withStatus(500)));

    client.get().uri("/api/v1/movies/500err").exchange().expectStatus().is5xxServerError();
  }

  /**
   * The dedicated cb-test route treats 500 as a circuit-breaker failure. After enough failures the
   * breaker must OPEN and serve the fallback: every request returns the 503 fallback body, and —
   * the load-bearing assertion — the downstream receives FEWER calls than were sent, proving the
   * open breaker short-circuits instead of forwarding.
   */
  @Test
  @DisplayName("Circuit breaker opens and serves the fallback")
  void circuitBreakerOpensAndServesFallback() {
    stubFor(
        get(urlPathMatching("/api/v1/movies/cbtest/.*")).willReturn(aResponse().withStatus(500)));

    int attempts = 12;
    for (int i = 0; i < attempts; i++) {
      client
          .get()
          .uri("/api/v1/movies/cbtest/" + i)
          .exchange()
          // Both the pre-open failures and the post-open short-circuits
          // resolve to the fallback controller (503, ApiResponse).
          .expectStatus()
          .isEqualTo(503)
          .expectBody()
          .jsonPath("$.success")
          .isEqualTo(false)
          .jsonPath("$.message")
          .value(m -> assertThat((String) m).contains("Movie Service"));
    }

    // The breaker (min 4 calls, window 4) must have opened, so the downstream
    // saw fewer than all 12 requests.
    verify(lessThan(attempts), getRequestedFor(urlPathMatching("/api/v1/movies/cbtest/.*")));
  }

  /**
   * The rate limiter (burst 3, replenish 1/s, keyed on X-Forwarded-For) must allow the burst then
   * reject with 429. Firing a fixed client IP rapidly, we expect at least one success AND at least
   * one 429 — asserted tolerantly because exact counts depend on token-refill timing.
   */
  @Test
  @DisplayName("Rate limiter returns 429 after the burst is exhausted")
  void rateLimiterRejectsAfterBurst() {
    stubFor(get(urlPathMatching("/api/v1/movies/rltest/.*")).willReturn(okJson("{}")));

    AtomicInteger ok = new AtomicInteger();
    AtomicInteger throttled = new AtomicInteger();
    for (int i = 0; i < 10; i++) {
      int status =
          client
              .get()
              .uri("/api/v1/movies/rltest/1")
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
   * A CORS preflight (OPTIONS) from a configured origin must be answered with the matching {@code
   * Access-Control-Allow-Origin} header, so the browser lets the React app (localhost:3000) call
   * the gateway cross-origin.
   */
  @Test
  @DisplayName("CORS preflight is allowed for a configured origin")
  void corsPreflightAllowsConfiguredOrigin() {
    client
        .options()
        .uri("/api/v1/movies/550")
        .header(HttpHeaders.ORIGIN, "http://localhost:3000")
        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
        .exchange()
        .expectStatus()
        .is2xxSuccessful()
        .expectHeader()
        .valueEquals("Access-Control-Allow-Origin", "http://localhost:3000");
  }

  // ------------------------------------------------------------------
  // #33 — TMDB v3 facade routing + auth/account proxy
  // ------------------------------------------------------------------

  /**
   * The bare TMDB catalog paths the React app calls (`/movie/...`, `/genre/...`, `/discover/...`,
   * `/search/...`) must route to the movie-service facade — this is what lets the app swap its base
   * URL to the gateway. Verifies the path reaches the downstream unchanged.
   */
  @Test
  @DisplayName("Facade: TMDB movie catalog paths route to movie-service")
  void facadeRoutesMovieCatalogToDownstream() {
    stubFor(
        get(urlPathEqualTo("/movie/popular")).willReturn(okJson("{\"page\":1,\"results\":[]}")));
    stubFor(get(urlPathEqualTo("/genre/movie/list")).willReturn(okJson("{\"genres\":[]}")));
    stubFor(get(urlPathEqualTo("/search/movie")).willReturn(okJson("{\"results\":[]}")));

    client.get().uri("/movie/popular?page=1").exchange().expectStatus().isOk();
    client.get().uri("/genre/movie/list").exchange().expectStatus().isOk();
    client.get().uri("/search/movie?query=matrix").exchange().expectStatus().isOk();

    verify(getRequestedFor(urlPathEqualTo("/movie/popular")));
    verify(getRequestedFor(urlPathEqualTo("/genre/movie/list")));
    verify(getRequestedFor(urlPathEqualTo("/search/movie")));
  }

  /**
   * The gateway must strip any client-sent {@code api_key} from facade requests so a leaked/guessed
   * key never reaches (or is honored by) a downstream — the downstream injects the real server-side
   * key itself. Asserts the downstream saw NO api_key parameter.
   */
  @Test
  @DisplayName("Facade: strips the client-sent api_key before the downstream")
  void facadeStripsClientApiKey() {
    stubFor(get(urlPathEqualTo("/movie/550")).willReturn(okJson("{\"id\":550}")));

    client.get().uri("/movie/550?api_key=leaked-client-key").exchange().expectStatus().isOk();

    verify(getRequestedFor(urlPathEqualTo("/movie/550")).withQueryParam("api_key", absent()));
  }

  /**
   * `/person/{id}` must route to the actor-service facade — asserted against the actor stand-in
   * (9972) AND against the movie stand-in receiving nothing, so a predicate change that swallowed
   * /person into the movie route would fail here.
   */
  @Test
  @DisplayName("Facade: /person routes to actor-service, not movie-service")
  void facadeRoutesPersonToActorService() {
    actorMock.stubFor(
        get(urlPathEqualTo("/person/819"))
            .willReturn(okJson("{\"id\":819,\"name\":\"Edward Norton\"}")));

    client.get().uri("/person/819").exchange().expectStatus().isOk();

    actorMock.verify(getRequestedFor(urlPathEqualTo("/person/819")));
    verify(0, getRequestedFor(urlPathEqualTo("/person/819")));
  }

  /**
   * The /search namespace is shared across TMDB resource types, and routes are matched in
   * declaration order — so a blanket {@code /search/**} on the movie route would swallow person
   * searches and 404 them. This pins the split: {@code /search/person} must reach actor-service and
   * {@code /search/movie} must reach movie-service, each with the other downstream untouched. It is
   * the regression guard for the trap called out in the route config's own comment.
   */
  @Test
  @DisplayName("Facade: /search is split by resource type across downstreams")
  void facadeSplitsSearchNamespaceByResourceType() {
    // Given: both downstreams can answer their own half of /search.
    stubFor(get(urlPathEqualTo("/search/movie")).willReturn(okJson("{\"results\":[]}")));
    actorMock.stubFor(get(urlPathEqualTo("/search/person")).willReturn(okJson("{\"results\":[]}")));

    // When: one search of each type goes through the gateway.
    client.get().uri("/search/movie?query=matrix").exchange().expectStatus().isOk();
    client.get().uri("/search/person?query=norton").exchange().expectStatus().isOk();

    // Then: each landed on its own service and neither leaked to the other.
    verify(getRequestedFor(urlPathEqualTo("/search/movie")));
    verify(0, getRequestedFor(urlPathEqualTo("/search/person")));
    actorMock.verify(getRequestedFor(urlPathEqualTo("/search/person")));
    actorMock.verify(0, getRequestedFor(urlPathEqualTo("/search/movie")));
  }

  /**
   * `/discover/movie` is how the React app filters the catalog by genre, and it is the one facade
   * path with no coverage elsewhere in this class. It must route to movie-service with the query
   * string intact — the filter parameters are the entire point of the endpoint.
   */
  @Test
  @DisplayName("Facade: /discover/movie routes to movie-service with filters intact")
  void facadeRoutesDiscoverToMovieService() {
    stubFor(
        get(urlPathEqualTo("/discover/movie")).willReturn(okJson("{\"page\":1,\"results\":[]}")));

    client.get().uri("/discover/movie?with_genres=28&page=2").exchange().expectStatus().isOk();

    verify(
        getRequestedFor(urlPathEqualTo("/discover/movie"))
            .withQueryParam("with_genres", equalTo("28"))
            .withQueryParam("page", equalTo("2")));
  }

  /**
   * api_key stripping must hold on the person route too, not just the movie one — the filter is
   * configured per-route, so it is genuinely possible to add a facade route and forget it, leaking
   * a client-supplied key to a downstream that would otherwise use the server-side key.
   */
  @Test
  @DisplayName("Facade: strips the client api_key on the person route as well")
  void facadeStripsClientApiKeyOnPersonRoute() {
    actorMock.stubFor(get(urlPathEqualTo("/person/819")).willReturn(okJson("{\"id\":819}")));

    client.get().uri("/person/819?api_key=leaked-client-key").exchange().expectStatus().isOk();

    actorMock.verify(
        getRequestedFor(urlPathEqualTo("/person/819")).withQueryParam("api_key", absent()));
  }

  /**
   * TMDB's authentication endpoints are proxied straight to the real TMDB so the app's login keeps
   * using the user's TMDB account. The gateway must (a) restore the {@code /3} base path and (b)
   * replace the client's api_key with the server-side key. Asserts the upstream received {@code
   * /3/authentication/token/new?api_key=server-side-key}.
   */
  @Test
  @DisplayName("Proxy: /authentication injects server key and prefixes /3")
  void authProxyInjectsServerKeyAndPrefix() {
    stubFor(
        get(urlPathEqualTo("/3/authentication/token/new"))
            .willReturn(okJson("{\"success\":true,\"request_token\":\"rt-123\"}")));

    client
        .get()
        .uri("/authentication/token/new?api_key=client-key")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.request_token")
        .isEqualTo("rt-123");

    verify(
        getRequestedFor(urlPathEqualTo("/3/authentication/token/new"))
            .withQueryParam("api_key", equalTo("server-side-key")));
  }

  /**
   * TMDB's own responses carry {@code Access-Control-Allow-Origin: *}, and the gateway's CORS
   * filter also sets that header — two values on one response, which browsers reject outright as
   * invalid.
   */
  @Test
  @DisplayName("Proxy: /authentication does not duplicate Access-Control-Allow-Origin")
  void authProxyDoesNotDuplicateCorsHeader() {
    stubFor(
        get(urlPathEqualTo("/3/authentication/token/new"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withHeader("Access-Control-Allow-Origin", "*")
                    .withBody("{\"success\":true,\"request_token\":\"rt-123\"}")));

    EntityExchangeResult<Void> result =
        client
            .get()
            .uri("/authentication/token/new?api_key=client-key")
            .header(HttpHeaders.ORIGIN, "http://localhost:3000")
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody(Void.class)
            .returnResult();

    assertThat(result.getResponseHeaders().get(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN))
        .containsExactly("http://localhost:3000");
  }

  /**
   * Account actions (favorites/watchlist) are also proxied to real TMDB. A POST must reach {@code
   * /3/account/{id}/favorite} with the server key injected AND the user's {@code session_id}
   * forwarded untouched — both are required for TMDB to accept the write against the right account.
   */
  @Test
  @DisplayName("Proxy: /account POST forwards session_id and injects server key")
  void accountProxyForwardsSessionAndInjectsKey() {
    stubFor(
        post(urlPathEqualTo("/3/account/42/favorite"))
            .willReturn(okJson("{\"success\":true,\"status_code\":1}")));

    client
        .post()
        .uri(
            b ->
                b.path("/account/42/favorite")
                    .queryParam("session_id", "sess-abc")
                    .queryParam("api_key", "client-key")
                    .build())
        .bodyValue("{\"media_type\":\"movie\",\"media_id\":550,\"favorite\":true}")
        .exchange()
        .expectStatus()
        .isOk();

    verify(
        postRequestedFor(urlPathEqualTo("/3/account/42/favorite"))
            .withQueryParam("api_key", equalTo("server-side-key"))
            .withQueryParam("session_id", equalTo("sess-abc")));
  }

  /**
   * The proxy is a conduit, not an interpreter: when TMDB rejects a call the client must see TMDB's
   * own status AND its own body. The React app branches on TMDB's numeric {@code status_code}, so a
   * gateway that swallowed the body or normalized the status into a generic 500 would break error
   * handling (e.g. an expired session would stop being distinguishable from an outage).
   */
  @Test
  @DisplayName("Proxy: relays a TMDB error status and body verbatim")
  void authProxyRelaysUpstreamErrorVerbatim() {
    // Given: TMDB rejects the session as invalid.
    String tmdbError =
        "{\"success\":false,\"status_code\":7,\"status_message\":\"Invalid API key.\"}";
    stubFor(
        get(urlPathEqualTo("/3/authentication/session/new"))
            .willReturn(
                aResponse()
                    .withStatus(401)
                    .withHeader("Content-Type", "application/json")
                    .withBody(tmdbError)));

    // When/Then: the client sees TMDB's status and its exact body.
    client
        .get()
        .uri("/authentication/session/new?request_token=rt-bad")
        .exchange()
        .expectStatus()
        .isUnauthorized()
        .expectBody()
        .json(tmdbError);
  }

  /**
   * CORS is what actually lets the browser-side React app talk to the gateway, and the app calls
   * the BARE TMDB paths — so preflight has to succeed there, not merely on the /api/v1 surface the
   * other CORS test covers. Guards against the facade paths being added to the route table but left
   * outside the CORS configuration.
   */
  @Test
  @DisplayName("CORS preflight is allowed on the bare TMDB facade paths")
  void corsPreflightAllowedOnFacadePath() {
    client
        .options()
        .uri("/movie/popular")
        .header(HttpHeaders.ORIGIN, "http://localhost:3000")
        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
        .exchange()
        .expectStatus()
        .is2xxSuccessful()
        .expectHeader()
        .valueEquals("Access-Control-Allow-Origin", "http://localhost:3000");
  }

  /**
   * Mints a valid HS256 JWT with the gateway's expected claim set (sub / userId / roles), signed
   * with the shared secret.
   *
   * @param username subject claim
   * @param userId userId claim
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
