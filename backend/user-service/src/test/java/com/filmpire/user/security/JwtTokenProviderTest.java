package com.filmpire.user.security;

import com.filmpire.user.model.Role;
import com.filmpire.user.model.User;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link JwtTokenProvider}: token issuance, the
 * gateway-compatible claim contract, and rejection of tampered/expired
 * tokens.
 */
@DisplayName("JwtTokenProvider Unit Tests")
class JwtTokenProviderTest {

    /** Same secret shape the test profile uses (>= 256 bits for HS256). */
    private static final String SECRET = "test-secret-key-for-user-service-tests-256bit";

    private JwtTokenProvider provider;
    private User user;

    /** Fresh provider and a canonical user for every test. */
    @BeforeEach
    void setUp() {
        provider = new JwtTokenProvider(SECRET, 3_600_000L);
        user = User.builder()
            .id(UUID.randomUUID())
            .username("liviu")
            .email("liviu@example.com")
            .passwordHash("irrelevant")
            .role(Role.USER)
            .enabled(true)
            .accountNonLocked(true)
            .createdAt(LocalDateTime.now())
            .build();
    }

    @Test
    @DisplayName("Issued token carries the gateway claim contract: sub, userId, roles")
    void tokenCarriesGatewayClaims() {
        String token = provider.generateAccessToken(user);

        Optional<Claims> claims = provider.parse(token);

        assertThat(claims).isPresent();
        // These three claims are what the API gateway's JwtUtil reads —
        // this test IS the cross-service contract check.
        assertThat(claims.get().getSubject()).isEqualTo("liviu");
        assertThat(claims.get().get("userId", String.class))
            .isEqualTo(user.getId().toString());
        assertThat(claims.get().get("roles")).isEqualTo(List.of("USER"));
    }

    @Test
    @DisplayName("Tampered token is rejected (empty), not thrown")
    void tamperedTokenRejected() {
        String token = provider.generateAccessToken(user);
        // Flip a character in the signature segment.
        String tampered = token.substring(0, token.length() - 2) + "xx";

        assertThat(provider.parse(tampered)).isEmpty();
    }

    @Test
    @DisplayName("Token signed with a different secret is rejected")
    void wrongSecretRejected() {
        JwtTokenProvider other =
            new JwtTokenProvider("another-secret-key-that-is-long-enough-256bit!", 3_600_000L);
        String token = other.generateAccessToken(user);

        assertThat(provider.parse(token)).isEmpty();
    }

    @Test
    @DisplayName("Expired token is rejected")
    void expiredTokenRejected() {
        // Negative lifetime → token is born expired.
        JwtTokenProvider shortLived = new JwtTokenProvider(SECRET, -1_000L);
        String token = shortLived.generateAccessToken(user);

        assertThat(shortLived.parse(token)).isEmpty();
    }
}
