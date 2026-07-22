package com.filmpire.user.security;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Servlet filter that authenticates requests carrying a Bearer JWT.
 *
 * <p>On a valid token it populates the {@link SecurityContextHolder} with a
 * {@link UsernamePasswordAuthenticationToken} whose principal is the JWT
 * subject (username) and whose authorities are the {@code roles} claim with
 * the {@code ROLE_} prefix. Invalid or absent tokens simply leave the
 * context empty — the authorization rules in {@code SecurityConfig} then
 * produce the 401.</p>
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider tokenProvider;

    /**
     * Extracts and validates the Bearer token, populating the security
     * context on success.
     *
     * @param request     incoming request
     * @param response    outgoing response
     * @param filterChain remaining filter chain
     * @throws ServletException on downstream filter failure
     * @throws IOException      on downstream I/O failure
     */
    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain)
            throws ServletException, IOException {

        // 1. Pull the compact JWT out of "Authorization: Bearer <token>".
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7);

            // 2. Valid token → authenticated context; invalid → leave empty.
            tokenProvider.parse(token).ifPresent(claims ->
                SecurityContextHolder.getContext()
                    .setAuthentication(toAuthentication(claims)));
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Maps JWT claims to a Spring Security authentication.
     *
     * @param claims validated claims
     * @return authentication with username principal and ROLE_* authorities
     */
    private UsernamePasswordAuthenticationToken toAuthentication(Claims claims) {
        Object rolesClaim = claims.get("roles");
        List<SimpleGrantedAuthority> authorities =
            rolesClaim instanceof List<?> roles
                ? roles.stream()
                    .map(String::valueOf)
                    .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                    .toList()
                : List.of();

        return new UsernamePasswordAuthenticationToken(claims.getSubject(), null, authorities);
    }
}
