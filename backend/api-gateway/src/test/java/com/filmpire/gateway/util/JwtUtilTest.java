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
 * Unit tests for JwtUtil.
 */
@DisplayName("JwtUtil Tests")
class JwtUtilTest {

    private JwtUtil jwtUtil;
    private String testSecret = "test-secret-key-for-jwt-token-validation-must-be-long-enough";
    private SecretKey secretKey;

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil(testSecret);
        secretKey = Keys.hmacShaKeyFor(testSecret.getBytes(StandardCharsets.UTF_8));
    }

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

    @Test
    @DisplayName("Should return null for null Authorization header")
    void extractTokenFromHeader_shouldReturnNullForNullHeader() {
        // When
        String extractedToken = jwtUtil.extractTokenFromHeader(null);

        // Then
        assertThat(extractedToken).isNull();
    }

    // Helper methods

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

