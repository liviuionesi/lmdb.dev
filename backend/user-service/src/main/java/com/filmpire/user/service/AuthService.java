package com.filmpire.user.service;

import com.filmpire.shared.exception.UnauthorizedException;
import com.filmpire.shared.exception.ValidationException;
import com.filmpire.user.dto.AuthDtos.AuthResponse;
import com.filmpire.user.dto.AuthDtos.LoginRequest;
import com.filmpire.user.dto.AuthDtos.RegisterRequest;
import com.filmpire.user.dto.AuthDtos.UserProfileResponse;
import com.filmpire.user.model.RefreshToken;
import com.filmpire.user.model.Role;
import com.filmpire.user.model.User;
import com.filmpire.user.repository.RefreshTokenRepository;
import com.filmpire.user.repository.UserRepository;
import com.filmpire.user.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Authentication use-cases: register, login, refresh (with rotation) and
 * logout (ARCHITECTURE.md §3.5, issue #17).
 *
 * <p>Token model: short-lived JWT access tokens (stateless, validated by
 * this service AND the gateway with the shared secret) plus opaque,
 * database-backed refresh tokens (revocable — see {@link RefreshToken}).
 * Every refresh ROTATES the token: the presented one is deleted and a new
 * one issued, so a stolen refresh token stops working the moment the
 * legitimate client refreshes.</p>
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AuthService {

    /** Entropy for opaque refresh tokens (32 random bytes → 43-char value). */
    private static final int REFRESH_TOKEN_BYTES = 32;

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;
    private final SecureRandom secureRandom = new SecureRandom();

    /** Refresh-token lifetime in ms ({@code jwt.refresh-expiration}, 7 days). */
    @Value("${jwt.refresh-expiration:604800000}")
    private long refreshExpirationMs;

    /**
     * Registers a new account and signs it in immediately.
     *
     * @param request validated registration data
     * @return auth response with fresh access + refresh tokens
     * @throws ValidationException if the username or email is already taken
     */
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        // 1. Uniqueness guards (DB constraints back these up under race).
        if (userRepository.existsByUsername(request.username())) {
            throw new ValidationException("Username is already taken: " + request.username());
        }
        if (userRepository.existsByEmail(request.email())) {
            throw new ValidationException("Email is already registered: " + request.email());
        }

        // 2. Persist with the password BCrypt-hashed — raw password is
        //    garbage-collected with the request.
        User user = userRepository.save(User.builder()
            .username(request.username())
            .email(request.email())
            .passwordHash(passwordEncoder.encode(request.password()))
            .role(Role.USER)
            .enabled(true)
            .accountNonLocked(true)
            .createdAt(LocalDateTime.now())
            .build());

        log.info("Registered new user '{}'", user.getUsername());

        // 3. Immediate sign-in: same token bundle as login.
        return issueTokens(user);
    }

    /**
     * Authenticates username + password.
     *
     * @param request login credentials
     * @return auth response with fresh access + refresh tokens
     * @throws UnauthorizedException on unknown user, wrong password, or a
     *                               disabled/locked account — deliberately the
     *                               SAME message for all cases, so responses
     *                               don't reveal which part failed
     */
    @Transactional
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByUsername(request.username())
            .filter(u -> passwordEncoder.matches(request.password(), u.getPasswordHash()))
            .filter(User::isEnabled)
            .filter(User::isAccountNonLocked)
            .orElseThrow(() -> new UnauthorizedException("Invalid username or password"));

        user.setLastLogin(LocalDateTime.now());

        log.info("User '{}' logged in", user.getUsername());
        return issueTokens(user);
    }

    /**
     * Exchanges a valid refresh token for a new token pair (rotation).
     *
     * @param rawRefreshToken the opaque token presented by the client
     * @return auth response with a NEW access token and a NEW refresh token
     * @throws UnauthorizedException if the token is unknown, already rotated,
     *                               or expired
     */
    @Transactional
    public AuthResponse refresh(String rawRefreshToken) {
        // 1. Look up by hash — raw tokens are never stored.
        RefreshToken stored = refreshTokenRepository.findByTokenHash(sha256(rawRefreshToken))
            .orElseThrow(() -> new UnauthorizedException("Invalid refresh token"));

        // 2. Expired tokens are rejected AND purged.
        if (stored.getExpiresAt().isBefore(LocalDateTime.now())) {
            refreshTokenRepository.delete(stored);
            throw new UnauthorizedException("Refresh token expired");
        }

        // 3. Rotate: the presented token dies with this use.
        User user = stored.getUser();
        refreshTokenRepository.delete(stored);

        log.debug("Rotated refresh token for '{}'", user.getUsername());
        return issueTokens(user);
    }

    /**
     * Logs the user out by revoking ALL their refresh tokens. Outstanding
     * access tokens stay valid until natural expiry (max 1 h) — the accepted
     * trade-off of stateless JWTs, documented in the failure-mode matrix.
     *
     * @param username account to log out
     */
    @Transactional
    public void logout(String username) {
        userRepository.findByUsername(username).ifPresent(user -> {
            refreshTokenRepository.deleteByUser(user);
            log.info("User '{}' logged out (all refresh tokens revoked)", username);
        });
    }

    /**
     * Issues the standard token bundle for an authenticated user: a JWT
     * access token plus a stored-and-hashed opaque refresh token.
     *
     * @param user authenticated account
     * @return complete auth response
     */
    private AuthResponse issueTokens(User user) {
        // 1. Opaque refresh token: 32 random bytes, base64url. Client keeps
        //    the raw value; we keep only its SHA-256.
        byte[] bytes = new byte[REFRESH_TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        String rawRefreshToken = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        refreshTokenRepository.save(RefreshToken.builder()
            .user(user)
            .tokenHash(sha256(rawRefreshToken))
            .expiresAt(LocalDateTime.now().plusSeconds(refreshExpirationMs / 1000))
            .createdAt(LocalDateTime.now())
            .build());

        // 2. Access JWT with the gateway-compatible claim set.
        String accessToken = tokenProvider.generateAccessToken(user);

        return new AuthResponse(
            accessToken,
            rawRefreshToken,
            "Bearer",
            tokenProvider.getExpirationMs(),
            toProfile(user)
        );
    }

    /**
     * Maps an account to its public profile representation.
     *
     * @param user account entity
     * @return profile DTO (no password hash)
     */
    static UserProfileResponse toProfile(User user) {
        return new UserProfileResponse(
            user.getId(),
            user.getUsername(),
            user.getEmail(),
            user.getRole().name(),
            user.getCreatedAt(),
            user.getLastLogin()
        );
    }

    /**
     * Hex-encoded SHA-256, used to store/lookup refresh tokens.
     *
     * @param value raw token
     * @return 64-char lowercase hex digest
     */
    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is mandated by the JCA spec — unreachable in practice.
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
