package dev.lmdb.gateway.filter;

import dev.lmdb.gateway.util.JwtUtil;
import jakarta.annotation.Nonnull;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * Custom WebFilter for JWT authentication in API Gateway. Validates JWT tokens in incoming request
 * headers and populates reactive security context.
 *
 * @author LMDB Development Team
 * @version 1.0.0
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class JwtAuthenticationFilter implements WebFilter {

  private final JwtUtil jwtUtil;

  /**
   * Filters requests to validate JWT tokens
   *
   * @param exchange the current server exchange
   * @param chain the web filter chain
   * @return Mono&lt;Void&gt; representing the completion of filter processing
   */
  @Override
  @Nonnull
  public Mono<Void> filter(@Nonnull ServerWebExchange exchange, @Nonnull WebFilterChain chain) {
    ServerHttpRequest request = exchange.getRequest();
    String path = request.getURI().getPath();

    // Skip JWT validation for public endpoints
    if (isPublicPath(path)) {
      return chain.filter(exchange);
    }

    // Extract Authorization header
    String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);

    // If no Authorization header, continue without authentication
    if (authHeader == null || !authHeader.startsWith("Bearer ")) {
      log.debug("No valid Authorization header found for path: {}", path);
      return chain.filter(exchange);
    }

    try {
      // Extract and validate token
      String token = jwtUtil.extractTokenFromHeader(authHeader);

      if (token == null || !jwtUtil.validateToken(token)) {
        log.warn("Invalid JWT token for path: {}", path);
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        return exchange.getResponse().setComplete();
      }

      // Extract user details from token
      String username = jwtUtil.extractUsername(token);
      String userId = jwtUtil.extractUserId(token);
      List<String> roles = jwtUtil.extractRoles(token);

      // Create authorities
      List<SimpleGrantedAuthority> authorities =
          roles.stream()
              .map(
                  role ->
                      role.startsWith("ROLE_")
                          ? new SimpleGrantedAuthority(role)
                          : new SimpleGrantedAuthority("ROLE_" + role))
              .toList();

      // Create Authentication object
      Authentication authentication =
          new UsernamePasswordAuthenticationToken(username, null, authorities);

      // Mutate request with user context headers for downstream microservices
      ServerHttpRequest mutatedRequest =
          request
              .mutate()
              .header("X-User-Id", userId != null ? userId : "")
              .header("X-Username", username != null ? username : "")
              .header("X-User-Roles", String.join(",", roles))
              .build();

      log.debug(
          "Successfully authenticated user: {} (ID: {}) for path: {}", username, userId, path);

      // Continue filter chain with mutated request and updated security context
      return chain
          .filter(exchange.mutate().request(mutatedRequest).build())
          .contextWrite(ReactiveSecurityContextHolder.withAuthentication(authentication));

    } catch (Exception e) {
      log.error("JWT authentication error for path: {}", path, e);
      exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
      return exchange.getResponse().setComplete();
    }
  }

  /**
   * Checks if the path is a public endpoint that doesn't require JWT validation
   *
   * @param path the request path
   * @return true if path is public, false otherwise
   */
  private boolean isPublicPath(String path) {
    return path.startsWith("/api/v1/auth/login")
        || path.startsWith("/api/v1/auth/register")
        || path.startsWith("/api/v1/auth/refresh")
        || path.startsWith("/actuator")
        || path.startsWith("/fallback")
        || (path.startsWith("/api/v1/movies") && !path.contains("/admin"))
        || (path.startsWith("/api/v1/actors") && !path.contains("/admin"));
  }
}
