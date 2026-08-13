package dev.lmdb.gateway.filter;

import dev.lmdb.gateway.service.ActivityTrackingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Global filter for tracking backend user activity across the API Gateway.
 *
 * <p>Records timestamps of all real user requests (movies, actors, authentication, AI, search)
 * while intentionally ignoring internal actuator health checks, metric probes, and static fallbacks
 * so that automated monitoring does not prevent the 1-hour idle shutdown.
 *
 * @author LMDB Development Team
 * @version 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ActivityTrackingFilter implements GlobalFilter, Ordered {

  private final ActivityTrackingService activityTrackingService;

  /**
   * Filters incoming requests to record activity for non-monitoring endpoints.
   *
   * @param exchange the current server exchange
   * @param chain the gateway filter chain
   * @return Mono<Void> representing filter completion
   */
  @Override
  public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
    String path = exchange.getRequest().getURI().getPath();

    // Do not count health probes or fallback error routes as user activity
    if (!isMonitoringOrFallbackPath(path)) {
      activityTrackingService.recordActivity();
      log.debug("User activity recorded on path: {}", path);
    }

    return chain.filter(exchange);
  }

  /**
   * Determines if a given URI path is an automated health check, metric query, or fallback route.
   *
   * @param path request URI path
   * @return true if the path is a monitoring probe
   */
  private boolean isMonitoringOrFallbackPath(String path) {
    if (path == null) {
      return false;
    }
    return path.startsWith("/actuator")
        || path.startsWith("/fallback")
        || path.equals("/favicon.ico");
  }

  /**
   * Sets filter precedence. Runs near the top of the filter chain.
   *
   * @return order index
   */
  @Override
  public int getOrder() {
    return Ordered.HIGHEST_PRECEDENCE + 5;
  }
}
