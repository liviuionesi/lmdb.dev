package dev.lmdb.gateway.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.lmdb.gateway.util.JwtUtil;
import java.util.Arrays;
import java.util.Objects;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * Static null-safety helpers shared by the filter tests.
 *
 * <p>Spring's reactive mock builders are declared {@code @Nullable}, which trips the project's
 * null-checking; these wrappers assert non-null once so the test bodies stay free of repeated
 * Objects.requireNonNull noise.
 */
final class TestNullSafety {
  /**
   * Wraps a mock request in a web exchange, asserting the result is non-null.
   *
   * @param request the mock request
   * @return a non-null exchange
   */
  @NonNull
  static ServerWebExchange createExchange(@NonNull MockServerHttpRequest request) {
    return Objects.requireNonNull(
        MockServerWebExchange.from(request), "MockServerWebExchange must not be null");
  }

  /**
   * Null-asserts an exchange for passing into non-null parameters.
   *
   * @param exchange the exchange
   * @return the same exchange, guaranteed non-null
   */
  @NonNull
  static ServerWebExchange requireNonNull(ServerWebExchange exchange) {
    return Objects.requireNonNull(exchange, "Exchange must not be null");
  }

  /**
   * Null-asserts a filter chain for passing the mock into non-null parameters.
   *
   * @param chain the filter chain
   * @return the same chain, guaranteed non-null
   */
  @NonNull
  static WebFilterChain requireNonNull(WebFilterChain chain) {
    return Objects.requireNonNull(chain, "Filter chain must not be null");
  }
}

/**
 * Unit tests for {@link JwtAuthenticationFilter}, the gateway's reactive authentication gate.
 *
 * <p>{@link JwtUtil} and the downstream {@link WebFilterChain} are mocked, so these tests isolate
 * the filter's own decisions: which paths bypass auth, and how valid vs invalid tokens are handled.
 * Reactive flows are asserted with {@link StepVerifier}; the 401 case is checked on the mutated
 * response status because the filter short-circuits the chain rather than throwing.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("JwtAuthenticationFilter Tests")
class JwtAuthenticationFilterTest {

  @Mock private JwtUtil jwtUtil;

  @Mock private WebFilterChain filterChain;

  private JwtAuthenticationFilter jwtAuthenticationFilter;

  /** Builds the filter under test around the mocked JwtUtil. */
  @BeforeEach
  void setUp() {
    jwtAuthenticationFilter = new JwtAuthenticationFilter(jwtUtil);
  }

  /**
   * Public paths (login, catalog reads, health) must pass through with no token — the gateway
   * cannot demand auth on the endpoints used to obtain a token, nor on anonymous browsing.
   * Parameterized over representative public prefixes.
   */
  @ParameterizedTest
  @ValueSource(strings = {"/api/v1/auth/login", "/api/v1/movies/123", "/actuator/health"})
  @DisplayName("Should allow public paths without authentication")
  void filter_shouldAllowPublicPaths(String path) {
    // Given
    MockServerHttpRequest request =
        Objects.requireNonNull(MockServerHttpRequest.get(path).build(), "Request must not be null");
    ServerWebExchange exchange = TestNullSafety.createExchange(request);
    WebFilterChain chain = TestNullSafety.requireNonNull(filterChain);
    doReturn(Mono.empty()).when(chain).filter(any(ServerWebExchange.class));

    // When
    Mono<Void> result = jwtAuthenticationFilter.filter(exchange, chain);

    // Then
    StepVerifier.create(result).verifyComplete();
  }

  /**
   * A protected path presenting a token that fails validation must be refused with 401 — not passed
   * through — so unverified callers never reach downstream services. The status is asserted on the
   * response the filter mutated before completing.
   */
  @Test
  @DisplayName("Should reject invalid JWT token")
  void filter_shouldRejectInvalidToken() {
    // Given
    String invalidToken = "invalid.jwt.token";
    MockServerHttpRequest request =
        Objects.requireNonNull(
            MockServerHttpRequest.get("/api/v1/users/profile")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + invalidToken)
                .build(),
            "Request must not be null");
    ServerWebExchange exchange = TestNullSafety.createExchange(request);

    when(jwtUtil.extractTokenFromHeader(anyString())).thenReturn(invalidToken);
    when(jwtUtil.validateToken(invalidToken)).thenReturn(false);

    // When
    Mono<Void> result =
        jwtAuthenticationFilter.filter(exchange, TestNullSafety.requireNonNull(filterChain));

    // Then
    StepVerifier.create(result).verifyComplete();

    assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  /**
   * A valid token on a protected path must let the request continue down the chain. The stubbed
   * claim extractions (username/userId/roles) represent the identity the filter forwards
   * downstream; completing the chain proves the request was admitted rather than short-circuited.
   */
  @Test
  @DisplayName("Should process valid JWT token")
  void filter_shouldProcessValidToken() {
    // Given
    String validToken = "valid.jwt.token";
    MockServerHttpRequest request =
        Objects.requireNonNull(
            MockServerHttpRequest.get("/api/v1/users/profile")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + validToken)
                .build(),
            "Request must not be null");
    ServerWebExchange exchange = TestNullSafety.createExchange(request);
    WebFilterChain chain = TestNullSafety.requireNonNull(filterChain);

    when(jwtUtil.extractTokenFromHeader(anyString())).thenReturn(validToken);
    when(jwtUtil.validateToken(validToken)).thenReturn(true);
    when(jwtUtil.extractUsername(validToken)).thenReturn("testuser");
    when(jwtUtil.extractUserId(validToken)).thenReturn("user123");
    when(jwtUtil.extractRoles(validToken)).thenReturn(Arrays.asList("USER"));
    doReturn(Mono.empty()).when(chain).filter(any());

    // When
    Mono<Void> result = jwtAuthenticationFilter.filter(exchange, chain);

    // Then
    StepVerifier.create(result).verifyComplete();
  }

  /**
   * A request that reaches a protected path with no {@code Authorization} header is forwarded
   * unauthenticated rather than refused, so it is the one path on which a client-supplied {@code
   * X-User-Id} could otherwise travel downstream untouched. Downstream services treat that header
   * as proof of an authenticated caller, so the filter must remove it here.
   *
   * <p>Without this strip, a caller could name any user simply by setting the header, and every
   * downstream per-user ownership check would compare an attacker-chosen id against itself.
   */
  @Test
  @DisplayName("Should strip a client-supplied X-User-Id when no token is presented")
  void filter_shouldStripForgedIdentityHeadersWhenUnauthenticated() {
    // Given: a protected path, no Authorization header, but a forged identity header
    MockServerHttpRequest request =
        Objects.requireNonNull(
            MockServerHttpRequest.get("/api/v1/ai/chat")
                .header("X-User-Id", "victim-user-id")
                .header("X-Username", "victim")
                .header("X-User-Roles", "ROLE_ADMIN")
                .build(),
            "Request must not be null");
    ServerWebExchange exchange = TestNullSafety.createExchange(request);
    WebFilterChain chain = TestNullSafety.requireNonNull(filterChain);
    doReturn(Mono.empty()).when(chain).filter(any());

    // When
    StepVerifier.create(jwtAuthenticationFilter.filter(exchange, chain)).verifyComplete();

    // Then: nothing downstream ever sees the forged headers
    HttpHeaders forwarded = capturedForwardedHeaders(chain);
    assertThat(forwarded.get("X-User-Id")).isNull();
    assertThat(forwarded.get("X-Username")).isNull();
    assertThat(forwarded.get("X-User-Roles")).isNull();
  }

  /**
   * Public paths skip token validation entirely and return before any identity is established, so
   * they need the same strip — a forged header on {@code /api/v1/movies/**} must not reach a
   * downstream service either.
   */
  @Test
  @DisplayName("Should strip a client-supplied X-User-Id on public paths too")
  void filter_shouldStripForgedIdentityHeadersOnPublicPaths() {
    // Given
    MockServerHttpRequest request =
        Objects.requireNonNull(
            MockServerHttpRequest.get("/api/v1/movies/123")
                .header("X-User-Id", "victim-user-id")
                .build(),
            "Request must not be null");
    ServerWebExchange exchange = TestNullSafety.createExchange(request);
    WebFilterChain chain = TestNullSafety.requireNonNull(filterChain);
    doReturn(Mono.empty()).when(chain).filter(any());

    // When
    StepVerifier.create(jwtAuthenticationFilter.filter(exchange, chain)).verifyComplete();

    // Then
    assertThat(capturedForwardedHeaders(chain).get("X-User-Id")).isNull();
  }

  /**
   * When a valid token IS presented alongside a forged header, the identity forwarded downstream
   * must be the one extracted from the token, not the one the client asked for — the strip happens
   * first, then the filter writes its own value.
   */
  @Test
  @DisplayName("Should forward the token's identity, not a client-supplied one")
  void filter_shouldOverrideForgedIdentityWithTokenIdentity() {
    // Given
    String validToken = "valid.jwt.token";
    MockServerHttpRequest request =
        Objects.requireNonNull(
            MockServerHttpRequest.get("/api/v1/ai/chat")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + validToken)
                .header("X-User-Id", "victim-user-id")
                .build(),
            "Request must not be null");
    ServerWebExchange exchange = TestNullSafety.createExchange(request);
    WebFilterChain chain = TestNullSafety.requireNonNull(filterChain);

    when(jwtUtil.extractTokenFromHeader(anyString())).thenReturn(validToken);
    when(jwtUtil.validateToken(validToken)).thenReturn(true);
    when(jwtUtil.extractUsername(validToken)).thenReturn("testuser");
    when(jwtUtil.extractUserId(validToken)).thenReturn("real-caller-id");
    when(jwtUtil.extractRoles(validToken)).thenReturn(Arrays.asList("USER"));
    doReturn(Mono.empty()).when(chain).filter(any());

    // When
    StepVerifier.create(jwtAuthenticationFilter.filter(exchange, chain)).verifyComplete();

    // Then: exactly one value, and it is the token's — the forged one was not appended alongside it
    assertThat(capturedForwardedHeaders(chain).get("X-User-Id")).containsExactly("real-caller-id");
  }

  /**
   * Captures the exchange the filter passed down the chain and returns its request headers — what a
   * downstream service would actually receive.
   *
   * @param chain the mocked filter chain the filter invoked
   * @return the headers on the forwarded request
   */
  private static HttpHeaders capturedForwardedHeaders(WebFilterChain chain) {
    ArgumentCaptor<ServerWebExchange> exchangeCaptor =
        ArgumentCaptor.forClass(ServerWebExchange.class);
    verify(chain).filter(exchangeCaptor.capture());
    return exchangeCaptor.getValue().getRequest().getHeaders();
  }
}
