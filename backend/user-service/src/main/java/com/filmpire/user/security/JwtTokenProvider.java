package com.filmpire.user.security;

import com.filmpire.user.model.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.Optional;

/**
 * Issues and validates JWT access tokens.
 *
 * <p>CONTRACT WITH THE GATEWAY: tokens must be verifiable by the API
 * gateway's {@code JwtUtil}, which shares the same {@code jwt.secret}
 * (HS256) and reads: {@code sub} = username, {@code userId} = account UUID
 * string, {@code roles} = list of role names. Changing any of these claim
 * names breaks gateway authentication — update both sides together.</p>
 */
@Component
@Slf4j
public class JwtTokenProvider {

    /** HMAC-SHA key derived from the shared {@code jwt.secret}. */
    private final SecretKey secretKey;

    /** Access-token lifetime in milliseconds ({@code jwt.expiration}). */
    private final long expirationMs;

    /**
     * Creates the provider from configuration.
     *
     * @param secret       shared HS256 secret (min 256 bits) — same value the
     *                     gateway validates with
     * @param expirationMs access-token lifetime in milliseconds
     */
    public JwtTokenProvider(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.expiration}") long expirationMs) {
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMs = expirationMs;
    }

    /**
     * Issues an access token for an authenticated user.
     *
     * @param user the authenticated account
     * @return signed compact JWT
     */
    public String generateAccessToken(User user) {
        Date now = new Date();
        return Jwts.builder()
            .subject(user.getUsername())
            .claim("userId", user.getId().toString())
            .claim("roles", List.of(user.getRole().name()))
            .issuedAt(now)
            .expiration(new Date(now.getTime() + expirationMs))
            .signWith(secretKey)
            .compact();
    }

    /**
     * Validates a token and extracts its claims.
     *
     * @param token compact JWT from the Authorization header
     * @return claims if the signature is valid and the token unexpired;
     *         empty otherwise (never throws — callers treat empty as 401)
     */
    public Optional<Claims> parse(String token) {
        try {
            return Optional.of(Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload());
        } catch (JwtException | IllegalArgumentException e) {
            // Invalid signature, malformed, or expired — all map to "not
            // authenticated", detail only logged at debug to avoid log noise.
            log.debug("JWT rejected: {}", e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Access-token lifetime, exposed for the auth response body.
     *
     * @return lifetime in milliseconds
     */
    public long getExpirationMs() {
        return expirationMs;
    }
}
