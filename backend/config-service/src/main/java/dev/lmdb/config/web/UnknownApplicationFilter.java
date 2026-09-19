package dev.lmdb.config.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Rejects requests for a config-server {@code {application}} this server has no file for (#244).
 *
 * <p>Spring Cloud Config's {@code EnvironmentController} answers any application name with HTTP 200
 * and whatever common properties matched, even one it never heard of — the response looks identical
 * to a real, empty configuration. This filter runs ahead of everything else (registered at {@link
 * Ordered#HIGHEST_PRECEDENCE}, before the Spring Security chain) and turns an unrecognised first
 * path segment into a 404 before the config server ever sees the request.
 *
 * <p>Only requests shaped like {@code /{application}/{profile}[/{label}]} are checked — that is
 * exactly two or three non-empty path segments. Everything else ({@code /actuator/**}, {@code
 * /encrypt}, {@code /{name}-{profile}.yml}, the root path) is left alone.
 */
public class UnknownApplicationFilter extends OncePerRequestFilter {

  private final Set<String> knownApplications;

  /**
   * @param knownApplications every {@code {application}} name this server actually serves a file
   *     for (see {@code config.server.known-applications})
   */
  public UnknownApplicationFilter(Set<String> knownApplications) {
    this.knownApplications = knownApplications;
  }

  /**
   * Passes through requests this filter doesn't apply to, and 404s a two/three-segment request
   * whose first segment isn't a known application.
   *
   * @param request the incoming request
   * @param response the response, written to directly on rejection
   * @param filterChain the rest of the chain, invoked when the request is allowed through
   * @throws ServletException per the servlet filter contract
   * @throws IOException per the servlet filter contract
   */
  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    String[] segments = splitPath(request.getRequestURI(), request.getContextPath());

    boolean notAnApplicationRequest = segments.length < 2 || segments.length > 3;
    boolean actuator = segments.length > 0 && "actuator".equals(segments[0]);
    if (notAnApplicationRequest || actuator || knownApplications.contains(segments[0])) {
      filterChain.doFilter(request, response);
      return;
    }

    // response.sendError() would trigger Boot's internal forward to /error, which runs back
    // through the filter chain on an ERROR dispatch — one Spring Security's chain also covers by
    // default, and it would replace this 404 with a 401 once access control is enabled. Writing
    // the response directly finishes it here, before that forward can happen.
    response.setStatus(HttpServletResponse.SC_NOT_FOUND);
    response.setContentType("application/json");
    response.getWriter().write("{\"status\":404,\"error\":\"Not Found\"}");
  }

  /**
   * Splits a request URI into non-empty path segments, with the servlet context path (if any)
   * removed first.
   *
   * @param requestUri the raw request URI, e.g. {@code /movie-service/default}
   * @param contextPath the servlet context path, empty when the app is deployed at root
   * @return the non-empty segments, in order
   */
  private String[] splitPath(String requestUri, String contextPath) {
    String path = requestUri.substring(contextPath.length());
    String trimmed = path.startsWith("/") ? path.substring(1) : path;
    return trimmed.isEmpty() ? new String[0] : trimmed.split("/");
  }

  /** Registers {@link UnknownApplicationFilter} ahead of the Spring Security filter chain. */
  @Configuration
  static class Registration {

    /**
     * @param knownApplicationsCsv comma-separated known application names from {@code
     *     config.server.known-applications}
     * @return the filter, wired at {@link Ordered#HIGHEST_PRECEDENCE} so it runs before Spring
     *     Security can authenticate (or reject) a request for an application that doesn't exist
     */
    @Bean
    FilterRegistrationBean<UnknownApplicationFilter> unknownApplicationFilter(
        @Value("${config.server.known-applications}") String knownApplicationsCsv) {
      Set<String> knownApplications = Set.of(knownApplicationsCsv.split("\\s*,\\s*"));
      FilterRegistrationBean<UnknownApplicationFilter> registration =
          new FilterRegistrationBean<>(new UnknownApplicationFilter(knownApplications));
      registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
      return registration;
    }
  }
}
