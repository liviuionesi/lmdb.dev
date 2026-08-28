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
 * <p>This filter is also the sole author of the {@code X-User-*} identity headers downstream
 * services trust (see {@code dev.lmdb.ai.security.CallerIdentity}). It therefore strips those
 * headers off every inbound request before doing anything else — including on public paths, where
 * no token is validated and the request would otherwise be forwarded exactly as the client sent it.
 * Without that strip, a client could assert any identity simply by setting the header itself, and
 * every downstream per-user ownership check would be comparing an attacker-chosen value against
 * itself.
 *
 * @author LMDB Development Team
 * @version 1.0.0
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class JwtAuthenticationFilter implements WebFilter {

  /** Header carrying the authenticated caller's user id to downstream services. */
  private static final String USER_ID_HEADER = "X-User-Id";

  /** Header carrying the authenticated caller's username to downstream services. */
  private static final String USERNAME_HEADER = "X-Username";

  /** Header carrying the authenticated caller's roles to downstream services. */
  private static final String USER_ROLES_HEADER = "X-User-Roles";

  /**
   * Identity headers only this filter may set. Any inbound copy is a forgery attempt and is
   * discarded before routing.
   */
  private static final List<String> IDENTITY_HEADERS =
      List.of(USER_ID_HEADER, USERNAME_HEADER, USER_ROLES_HEADER);

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
    // 1. Discard any client-supplied identity headers first, so every path below — public,
    //    unauthenticated, and authenticated alike — routes onward with only the identity this
    //    filter itself established, if any.
    ServerWebExchange sanitizedExchange = stripClientIdentityHeaders(exchange);
    ServerHttpRequest request = sanitizedExchange.getRequest();
    String path = request.getURI().getPath();

    // Skip JWT validation for public endpoints
    if (isPublicPath(path)) {
      return chain.filter(sanitizedExchange);
    }

    // Extract Authorization header
    String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);

    // If no Authorization header, continue without authentication — but still with the stripped
    // request, so an unauthenticated caller cannot smuggle an identity header downstream.
    if (authHeader == null || !authHeader.startsWith("Bearer ")) {
      log.debug("No valid Authorization header found for path: {}", path);
      return chain.filter(sanitizedExchange);
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
              .header(USER_ID_HEADER, userId != null ? userId : "")
              .header(USERNAME_HEADER, username != null ? username : "")
              .header(USER_ROLES_HEADER, String.join(",", roles))
              .build();

      log.debug(
          "Successfully authenticated user: {} (ID: {}) for path: {}", username, userId, path);

      // Continue filter chain with mutated request and updated security context
      return chain
          .filter(sanitizedExchange.mutate().request(mutatedRequest).build())
          .contextWrite(ReactiveSecurityContextHolder.withAuthentication(authentication));

    } catch (Exception e) {
      log.error("JWT authentication error for path: {}", path, e);
      exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
      return exchange.getResponse().setComplete();
    }
  }

  /**
   * Removes every {@link #IDENTITY_HEADERS} entry the client may have sent, returning an exchange
   * whose request carries none of them.
   *
   * <p>Applied unconditionally, before the public-path check and before any token is inspected:
   * these headers are downstream services' proof of who the caller is, so the only copy that may
   * ever reach a downstream service is one this filter wrote after validating a JWT.
   *
   * @param exchange the inbound exchange
   * @return the same exchange with the identity headers removed from its request
   */
  private static ServerWebExchange stripClientIdentityHeaders(ServerWebExchange exchange) {
    ServerHttpRequest stripped =
        exchange
            .getRequest()
            .mutate()
            .headers(headers -> IDENTITY_HEADERS.forEach(headers::remove))
            .build();
    return exchange.mutate().request(stripped).build();
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
