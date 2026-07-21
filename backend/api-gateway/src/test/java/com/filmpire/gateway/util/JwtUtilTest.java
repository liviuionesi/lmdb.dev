package com.filmpire.gateway.util;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link JwtUtil}, the gateway's token-inspection helper.
 *
 * <p>The gateway is the trust boundary: it validates and reads claims from
 * access tokens the user-service issues, and never calls back into that
 * service. These tests therefore mint tokens with the SAME HS256 secret and
 * claim layout the user-service uses (sub / userId / roles) and assert the
 * gateway reads them back correctly — this is the consuming half of the
 * cross-service JWT contract (its producing half lives in user-service's
 * JwtTokenProviderTest).</p>
 */
@DisplayName("JwtUtil Tests")
class JwtUtilTest {

    private JwtUtil jwtUtil;
    private String testSecret = "test-secret-key-for-jwt-token-validation-must-be-long-enough";
    private SecretKey secretKey;

    /** Fresh util plus a signing key derived from the same secret, so the
     *  test can forge tokens the util will accept. */
    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil(testSecret);
        secretKey = Keys.hmacShaKeyFor(testSecret.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * The subject claim carries the username; downstream authorization and
     * request-attribution both key off it, so extraction must be exact.
     */
    @Test
    @DisplayName("Should extract username from valid token")
    void extractUsername_shouldReturnUsername() {
        // Given
        String username = "testuser";
        String token = createTestToken(username, "user123", Arrays.asList("USER"));

        // When
        String extractedUsername = jwtUtil.extractUsername(token);

        // Then
        assertThat(extractedUsername).isEqualTo(username);
    }

    /**
     * The userId claim lets the gateway attribute a request to an account
     * without a DB lookup; a wrong or missing value would misroute per-user
     * concerns downstream, so it must round-trip precisely.
     */
    @Test
    @DisplayName("Should extract user ID from valid token")
    void extractUserId_shouldReturnUserId() {
        // Given
        String userId = "user123";
        String token = createTestToken("testuser", userId, Arrays.asList("USER"));

        // When
        String extractedUserId = jwtUtil.extractUserId(token);

        // Then
        assertThat(extractedUserId).isEqualTo(userId);
    }

    /**
     * Roles drive authorization decisions, so ALL roles in the claim must be
     * returned. Order-insensitive matching is used because the token is a set
     * of roles, not an ordered list — position carries no meaning.
     */
    @Test
    @DisplayName("Should extract roles from valid token")
    void extractRoles_shouldReturnRoles() {
        // Given
        List<String> roles = Arrays.asList("USER", "ADMIN");
        String token = createTestToken("testuser", "user123", roles);

        // When
        List<String> extractedRoles = jwtUtil.extractRoles(token);

        // Then
        assertThat(extractedRoles).containsExactlyInAnyOrderElementsOf(roles);
    }

    /**
     * A correctly-signed, unexpired token must validate — the baseline
     * positive case that the whole gateway auth filter depends on.
     */
    @Test
    @DisplayName("Should validate correct token")
    void validateToken_shouldReturnTrueForValidToken() {
        // Given
        String token = createTestToken("testuser", "user123", Arrays.asList("USER"));

        // When
        boolean isValid = jwtUtil.validateToken(token);

        // Then
        assertThat(isValid).isTrue();
    }

    /**
     * validateToken must reject an expired token even though its signature is
     * still valid — expiry is what bounds a session's lifetime, so a
     * signature-only check would let sessions live forever.
     */
    @Test
    @DisplayName("Should invalidate expired token")
    void validateToken_shouldReturnFalseForExpiredToken() {
        // Given
        String token = createExpiredToken("testuser", "user123");

        // When
        boolean isValid = jwtUtil.validateToken(token);

        // Then
        assertThat(isValid).isFalse();
    }

    /**
     * The isTokenExpired predicate must report true for a past-expiry token;
     * it is the building block validateToken relies on, tested directly so a
     * regression is pinpointed to the predicate rather than the caller.
     */
    @Test
    @DisplayName("Should check if token is expired")
    void isTokenExpired_shouldReturnTrueForExpiredToken() {
        // Given
        String token = createExpiredToken("testuser", "user123");

        // When
        boolean isExpired = jwtUtil.isTokenExpired(token);

        // Then
        assertThat(isExpired).isTrue();
    }

    /**
     * The complementary case: a fresh token must not be reported expired, or
     * every valid request would be rejected. Together with the previous test
     * this pins both sides of the expiry boundary.
     */
    @Test
    @DisplayName("Should check if token is not expired")
    void isTokenExpired_shouldReturnFalseForValidToken() {
        // Given
        String token = createTestToken("testuser", "user123", Arrays.asList("USER"));

        // When
        boolean isExpired = jwtUtil.isTokenExpired(token);

        // Then
        assertThat(isExpired).isFalse();
    }

    /**
     * A well-formed "Bearer &lt;token&gt;" header must yield the bare token
     * (prefix stripped) so the rest of the filter chain sees only the JWT.
     */
    @Test
    @DisplayName("Should extract token from Authorization header")
    void extractTokenFromHeader_shouldExtractToken() {
        // Given
        String token = "sample.jwt.token";
        String authHeader = "Bearer " + token;

        // When
        String extractedToken = jwtUtil.extractTokenFromHeader(authHeader);

        // Then
        assertThat(extractedToken).isEqualTo(token);
    }

    /**
     * A header without the "Bearer " scheme is not a token the gateway
     * understands; returning null (not a garbage substring) lets the caller
     * treat it as "unauthenticated" cleanly.
     */
    @Test
    @DisplayName("Should return null for invalid Authorization header")
    void extractTokenFromHeader_shouldReturnNullForInvalidHeader() {
        // Given
        String authHeader = "Invalid Header";

        // When
        String extractedToken = jwtUtil.extractTokenFromHeader(authHeader);

        // Then
        assertThat(extractedToken).isNull();
    }

    /**
     * A missing Authorization header (null) must not NPE — anonymous requests
     * are normal traffic and have to flow to the "no token" path safely.
     */
    @Test
    @DisplayName("Should return null for null Authorization header")
    void extractTokenFromHeader_shouldReturnNullForNullHeader() {
        // When
        String extractedToken = jwtUtil.extractTokenFromHeader(null);

        // Then
        assertThat(extractedToken).isNull();
    }

    // Helper methods

    /**
     * Mints a valid token (24h expiry) signed with the test secret, carrying
     * the gateway's expected claim set.
     *
     * @param username subject claim
     * @param userId   userId claim
     * @param roles    roles claim
     * @return a signed, currently-valid compact JWT
     */
    private String createTestToken(String username, String userId, List<String> roles) {
        return Jwts.builder()
                .subject(username)
                .claim("userId", userId)
                .claim("roles", roles)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 86400000)) // 24 hours
                .signWith(secretKey)
                .compact();
    }

    /**
     * Mints a token whose expiry is already in the past, for the negative
     * expiry/validation cases.
     *
     * @param username subject claim
     * @param userId   userId claim
     * @return a signed but already-expired compact JWT
     */
    private String createExpiredToken(String username, String userId) {
        return Jwts.builder()
                .subject(username)
                .claim("userId", userId)
                .claim("roles", Arrays.asList("USER"))
                .issuedAt(new Date(System.currentTimeMillis() - 200000))
                .expiration(new Date(System.currentTimeMillis() - 100000)) // Expired 100 seconds ago
                .signWith(secretKey)
                .compact();
    }
}

