package com.filmpire.gateway.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.filmpire.gateway.service.ActivityTrackingService;
import java.util.Objects;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * Unit tests for {@link ActivityTrackingFilter}.
 *
 * <p>Verifies that real user API traffic triggers activity recording, while automated actuator
 * probes and fallback error routes are ignored to permit auto-stop when idle.
 */
@DisplayName("ActivityTrackingFilter Tests")
class ActivityTrackingFilterTest {

  private ActivityTrackingService activityTrackingService;
  private ActivityTrackingFilter activityTrackingFilter;
  private GatewayFilterChain filterChain;

  /** Initializes mocks before each test. */
  @BeforeEach
  void setUp() {
    activityTrackingService = mock(ActivityTrackingService.class);
    activityTrackingFilter = new ActivityTrackingFilter(activityTrackingService);
    filterChain = mock(GatewayFilterChain.class);
    when(filterChain.filter(any())).thenReturn(Mono.empty());
  }

  /** User requests to movie catalogs must record activity so the idle timer resets. */
  @Test
  @DisplayName("Should record activity on movie catalog request")
  void filter_shouldRecordActivityOnUserRequest() {
    // Given
    MockServerHttpRequest request =
        Objects.requireNonNull(
            MockServerHttpRequest.get("/api/v1/movies/popular").build(),
            "Request must not be null");
    MockServerWebExchange exchange = MockServerWebExchange.from(request);

    // When
    Mono<Void> result = activityTrackingFilter.filter(exchange, filterChain);

    // Then
    StepVerifier.create(result).verifyComplete();
    verify(activityTrackingService).recordActivity();
    verify(filterChain).filter(exchange);
  }

  /** TMDB facade endpoints must record user activity. */
  @Test
  @DisplayName("Should record activity on TMDB facade request")
  void filter_shouldRecordActivityOnTmdbFacadeRequest() {
    // Given
    MockServerHttpRequest request =
        Objects.requireNonNull(
            MockServerHttpRequest.get("/movie/popular").build(), "Request must not be null");
    MockServerWebExchange exchange = MockServerWebExchange.from(request);

    // When
    Mono<Void> result = activityTrackingFilter.filter(exchange, filterChain);

    // Then
    StepVerifier.create(result).verifyComplete();
    verify(activityTrackingService).recordActivity();
  }

  /**
   * Actuator health check probes must NOT record activity to avoid keeping the cluster alive
   * forever.
   */
  @Test
  @DisplayName("Should ignore actuator health checks")
  void filter_shouldIgnoreActuatorHealthChecks() {
    // Given
    MockServerHttpRequest request =
        Objects.requireNonNull(
            MockServerHttpRequest.get("/actuator/health").build(), "Request must not be null");
    MockServerWebExchange exchange = MockServerWebExchange.from(request);

    // When
    Mono<Void> result = activityTrackingFilter.filter(exchange, filterChain);

    // Then
    StepVerifier.create(result).verifyComplete();
    verify(activityTrackingService, never()).recordActivity();
    verify(filterChain).filter(exchange);
  }

  /** Fallback endpoints must NOT record activity. */
  @Test
  @DisplayName("Should ignore fallback circuit breaker routes")
  void filter_shouldIgnoreFallbackRoutes() {
    // Given
    MockServerHttpRequest request =
        Objects.requireNonNull(
            MockServerHttpRequest.get("/fallback/movie").build(), "Request must not be null");
    MockServerWebExchange exchange = MockServerWebExchange.from(request);

    // When
    Mono<Void> result = activityTrackingFilter.filter(exchange, filterChain);

    // Then
    StepVerifier.create(result).verifyComplete();
    verify(activityTrackingService, never()).recordActivity();
  }

  /** Verifies filter order is HIGHEST_PRECEDENCE + 5. */
  @Test
  @DisplayName("Should return configured order")
  void getOrder_shouldReturnConfiguredOrder() {
    assertThat(activityTrackingFilter.getOrder())
        .isEqualTo(org.springframework.core.Ordered.HIGHEST_PRECEDENCE + 5);
  }
}
