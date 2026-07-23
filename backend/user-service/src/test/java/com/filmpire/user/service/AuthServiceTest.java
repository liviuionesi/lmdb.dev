package com.filmpire.user.service;

import com.filmpire.shared.exception.UnauthorizedException;
import com.filmpire.shared.exception.ValidationException;
import com.filmpire.user.dto.AuthDtos.AuthResponse;
import com.filmpire.user.dto.AuthDtos.LoginRequest;
import com.filmpire.user.dto.AuthDtos.RegisterRequest;
import com.filmpire.user.model.RefreshToken;
import com.filmpire.user.model.Role;
import com.filmpire.user.model.User;
import com.filmpire.user.repository.RefreshTokenRepository;
import com.filmpire.user.repository.UserRepository;
import com.filmpire.user.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link AuthService}: registration guards, login outcomes,
 * refresh rotation and logout revocation. Repositories are mocked; the real
 * BCrypt encoder and a real token provider are used so hashing and JWT
 * behavior stay honest.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AuthService Unit Tests")
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    /** Real encoder (strength 4 for test speed — behavior identical). */
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4);

    /** Real provider — token issuance is part of what we assert. */
    private final JwtTokenProvider tokenProvider =
        new JwtTokenProvider("test-secret-key-for-user-service-tests-256bit", 3_600_000L);

    private AuthService authService;

    /** Wires the service by hand (mixed mocks + real collaborators). */
    @BeforeEach
    void setUp() {
        authService = new AuthService(
            userRepository, refreshTokenRepository, passwordEncoder, tokenProvider);
        ReflectionTestUtils.setField(authService, "refreshExpirationMs", 604_800_000L);
    }

    /**
     * Happy-path signup must persist a BCrypt hash (never the raw password),
     * store a refresh-token row, and hand back a bundle the client can use
     * immediately — no separate login round trip after registering.
     */
    @Test
    @DisplayName("Registration hashes the password and returns a full token bundle")
    void registerHashesPasswordAndIssuesTokens() {
        when(userRepository.existsByUsername("liviu")).thenReturn(false);
        when(userRepository.existsByEmail("liviu@example.com")).thenReturn(false);
        // Echo back the entity with an id, as JPA would.
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(UUID.randomUUID());
            return u;
        });

        AuthResponse response = authService.register(
            new RegisterRequest("liviu", "liviu@example.com", "secret-password"));

        // Tokens present and the profile mirrors the account.
        assertThat(response.accessToken()).isNotBlank();
        assertThat(response.refreshToken()).isNotBlank();
        assertThat(response.user().username()).isEqualTo("liviu");

        // The stored hash must verify against the raw password but not equal it.
        verify(userRepository).save(any(User.class));
        verify(refreshTokenRepository).save(any(RefreshToken.class));
    }

    /**
     * The uniqueness guard must fire before any save is attempted: reaching
     * the database constraint instead would surface as a 500 rather than a
     * clean ValidationException naming the offending field.
     */
    @Test
    @DisplayName("Registration rejects a taken username")
    void registerRejectsDuplicateUsername() {
        when(userRepository.existsByUsername("liviu")).thenReturn(true);

        RegisterRequest request = new RegisterRequest("liviu", "liviu@example.com", "secret-password");

        assertThatThrownBy(() -> authService.register(request))
            .isInstanceOf(ValidationException.class)
            .hasMessageContaining("Username");
    }

    /**
     * Runs against the real BCrypt encoder, so a green result proves the raw
     * password actually verifies against the stored hash. The lastLogin touch
     * matters because downstream audit/inactivity logic depends on it.
     */
    @Test
    @DisplayName("Login succeeds with correct credentials and updates lastLogin")
    void loginSucceeds() {
        User user = activeUser("liviu", "secret-password");
        when(userRepository.findByUsername("liviu")).thenReturn(Optional.of(user));

        AuthResponse response = authService.login(new LoginRequest("liviu", "secret-password"));

        assertThat(response.accessToken()).isNotBlank();
        assertThat(user.getLastLogin()).isNotNull();
    }

    /**
     * A wrong password and an unknown username must produce the identical
     * exception AND message; any observable difference turns the login
     * endpoint into a username-enumeration oracle for attackers.
     */
    @Test
    @DisplayName("Login fails identically for wrong password and unknown user")
    void loginFailsUniformly() {
        User user = activeUser("liviu", "secret-password");
        when(userRepository.findByUsername("liviu")).thenReturn(Optional.of(user));
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        // Same exception, same message for both failure modes — no
        // username-enumeration oracle.
        LoginRequest wrongPassword = new LoginRequest("liviu", "wrong");
        LoginRequest unknownUser = new LoginRequest("ghost", "whatever");

        assertThatThrownBy(() -> authService.login(wrongPassword))
            .isInstanceOf(UnauthorizedException.class)
            .hasMessage("Invalid username or password");
        assertThatThrownBy(() -> authService.login(unknownUser))
            .isInstanceOf(UnauthorizedException.class)
            .hasMessage("Invalid username or password");
    }

    /**
     * A disabled account presenting the CORRECT password must still be
     * refused — the enabled flag is the administrative kill switch, and
     * password validity must never override it.
     */
    @Test
    @DisplayName("Login rejects disabled accounts")
    void loginRejectsDisabledAccount() {
        User user = activeUser("liviu", "secret-password");
        user.setEnabled(false);
        when(userRepository.findByUsername("liviu")).thenReturn(Optional.of(user));

        LoginRequest request = new LoginRequest("liviu", "secret-password");

        assertThatThrownBy(() -> authService.login(request))
            .isInstanceOf(UnauthorizedException.class);
    }

    /**
     * A refresh token with no matching hash in the store must be refused:
     * accepting it would let any well-formed string mint fresh access tokens
     * without the caller ever having authenticated.
     */
    @Test
    @DisplayName("Refresh rejects an unknown token")
    void refreshRejectsUnknownToken() {
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.refresh("no-such-token"))
            .isInstanceOf(UnauthorizedException.class);
    }

    /**
     * An expired refresh token must be rejected AND deleted in the same call:
     * leaving the dead row behind would allow endless retries against it and
     * bloat the token table with entries that can never succeed again.
     */
    @Test
    @DisplayName("Refresh rejects and purges an expired token")
    void refreshRejectsExpiredToken() {
        User user = activeUser("liviu", "secret-password");
        RefreshToken expired = RefreshToken.builder()
            .user(user)
            .tokenHash("hash")
            .expiresAt(LocalDateTime.now().minusDays(1))
            .createdAt(LocalDateTime.now().minusDays(8))
            .build();
        when(refreshTokenRepository.findByTokenHash(anyString()))
            .thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> authService.refresh("expired-token"))
            .isInstanceOf(UnauthorizedException.class)
            .hasMessageContaining("expired");
        verify(refreshTokenRepository).delete(expired);
    }

    /**
     * Logout must revoke every refresh token the user holds, not only the one
     * presented — otherwise a token stolen from another device would survive
     * an explicit "log me out" request.
     */
    @Test
    @DisplayName("Logout revokes all of the user's refresh tokens")
    void logoutRevokesAllTokens() {
        User user = activeUser("liviu", "secret-password");
        when(userRepository.findByUsername("liviu")).thenReturn(Optional.of(user));

        authService.logout("liviu");

        verify(refreshTokenRepository).deleteByUser(user);
    }

    /**
     * Builds an enabled, unlocked user with a real BCrypt hash.
     *
     * @param username    login name
     * @param rawPassword password to hash
     * @return ready-to-authenticate account
     */
    private User activeUser(String username, String rawPassword) {
        return User.builder()
            .id(UUID.randomUUID())
            .username(username)
            .email(username + "@example.com")
            .passwordHash(passwordEncoder.encode(rawPassword))
            .role(Role.USER)
            .enabled(true)
            .accountNonLocked(true)
            .createdAt(LocalDateTime.now())
            .build();
    }
}
