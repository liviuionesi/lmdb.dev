package com.filmpire.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Immutable request/response records for the user-service API
 * (ARCHITECTURE.md Appendix B: all DTOs are Java records).
 *
 * <p>Grouped in one container class because the records are small, always
 * used together, and the API surface reads best as a single contract page.</p>
 */
public final class AuthDtos {

    /** Not instantiable — pure record container. */
    private AuthDtos() {
    }

    /**
     * Registration request.
     *
     * @param username unique login name, 3–50 chars
     * @param email    unique, syntactically valid address
     * @param password raw password, min 8 chars (hashed immediately, never stored)
     */
    public record RegisterRequest(
        @NotBlank @Size(min = 3, max = 50) String username,
        @NotBlank @Email @Size(max = 255) String email,
        @NotBlank @Size(min = 8, max = 100) String password
    ) {
    }

    /**
     * Login request.
     *
     * @param username login name
     * @param password raw password to verify against the stored BCrypt hash
     */
    public record LoginRequest(
        @NotBlank String username,
        @NotBlank String password
    ) {
    }

    /**
     * Refresh / logout request carrying the opaque refresh token.
     *
     * @param refreshToken the raw opaque token previously issued
     */
    public record RefreshRequest(
        @NotBlank String refreshToken
    ) {
    }

    /**
     * Successful authentication response.
     *
     * @param accessToken  JWT for the {@code Authorization: Bearer} header
     * @param refreshToken opaque token for {@code POST /auth/refresh}; rotated
     *                     on every use
     * @param tokenType    always {@code Bearer}
     * @param expiresInMs  access-token lifetime in milliseconds
     * @param user         profile snapshot of the authenticated account
     */
    public record AuthResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresInMs,
        UserProfileResponse user
    ) {
    }

    /**
     * Public profile representation (never exposes the password hash).
     *
     * @param id        account UUID
     * @param username  login name
     * @param email     contact address
     * @param role      RBAC role name
     * @param createdAt registration time
     * @param lastLogin last successful login, may be null
     */
    public record UserProfileResponse(
        UUID id,
        String username,
        String email,
        String role,
        LocalDateTime createdAt,
        LocalDateTime lastLogin
    ) {
    }

    /**
     * Profile update request (currently: email only — username is immutable
     * because it is the JWT subject).
     *
     * @param email new contact address
     */
    public record UpdateProfileRequest(
        @NotBlank @Email @Size(max = 255) String email
    ) {
    }

    /**
     * Password change request.
     *
     * @param currentPassword must match the stored hash
     * @param newPassword     replacement, min 8 chars
     */
    public record ChangePasswordRequest(
        @NotBlank String currentPassword,
        @NotBlank @Size(min = 8, max = 100) String newPassword
    ) {
    }

    /**
     * One favorites/watchlist entry.
     *
     * @param movieId TMDB movie id (hydrate details via the TMDB facade)
     * @param addedAt when the entry was created
     */
    public record MovieListEntryResponse(
        Long movieId,
        LocalDateTime addedAt
    ) {
    }
}
