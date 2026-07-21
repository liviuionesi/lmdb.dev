package com.filmpire.user.controller;

import com.filmpire.shared.dto.ApiResponse;
import com.filmpire.user.dto.AuthDtos.ChangePasswordRequest;
import com.filmpire.user.dto.AuthDtos.MovieListEntryResponse;
import com.filmpire.user.dto.AuthDtos.UpdateProfileRequest;
import com.filmpire.user.dto.AuthDtos.UserProfileResponse;
import com.filmpire.user.service.UserAccountService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Profile, favorites and watchlist endpoints for the authenticated user
 * (issue #17). Every route requires a valid Bearer token; the acting user is
 * always the token's subject — no cross-user access exists.
 */
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@Tag(name = "Users", description = "Profile, favorites and watchlist of the authenticated user")
public class UserController {

    private final UserAccountService userAccountService;

    /**
     * Returns the caller's profile.
     *
     * @param auth caller identity (JWT subject)
     * @return 200 with the profile
     */
    @GetMapping("/profile")
    @Operation(summary = "Get the current user's profile")
    public ResponseEntity<ApiResponse<UserProfileResponse>> getProfile(Authentication auth) {
        return ok(userAccountService.getProfile(auth.getName()), "Profile retrieved");
    }

    /**
     * Updates the caller's profile.
     *
     * @param auth    caller identity
     * @param request new profile values
     * @return 200 with the updated profile
     */
    @PutMapping("/profile")
    @Operation(summary = "Update the current user's profile")
    public ResponseEntity<ApiResponse<UserProfileResponse>> updateProfile(
            Authentication auth, @Valid @RequestBody UpdateProfileRequest request) {
        return ok(userAccountService.updateProfile(auth.getName(), request), "Profile updated");
    }

    /**
     * Changes the caller's password.
     *
     * @param auth    caller identity
     * @param request current + new password
     * @return 200 acknowledgement
     */
    @PutMapping("/password")
    @Operation(summary = "Change the current user's password")
    public ResponseEntity<ApiResponse<Void>> changePassword(
            Authentication auth, @Valid @RequestBody ChangePasswordRequest request) {
        userAccountService.changePassword(auth.getName(), request);
        return ok(null, "Password changed");
    }

    /**
     * Lists the caller's favorite movies (TMDB ids), newest first.
     *
     * @param auth caller identity
     * @return 200 with the list
     */
    @GetMapping("/favorites")
    @Operation(summary = "List favorite movies")
    public ResponseEntity<ApiResponse<List<MovieListEntryResponse>>> getFavorites(Authentication auth) {
        return ok(userAccountService.getFavorites(auth.getName()), "Favorites retrieved");
    }

    /**
     * Adds a movie to favorites (idempotent — repeat adds succeed silently).
     *
     * @param auth    caller identity
     * @param movieId TMDB movie id
     * @return 200 acknowledgement
     */
    @PostMapping("/favorites/{movieId}")
    @Operation(summary = "Add a movie to favorites (idempotent)")
    public ResponseEntity<ApiResponse<Void>> addFavorite(
            Authentication auth, @PathVariable Long movieId) {
        userAccountService.addFavorite(auth.getName(), movieId);
        return ok(null, "Added to favorites");
    }

    /**
     * Removes a movie from favorites (idempotent).
     *
     * @param auth    caller identity
     * @param movieId TMDB movie id
     * @return 200 acknowledgement
     */
    @DeleteMapping("/favorites/{movieId}")
    @Operation(summary = "Remove a movie from favorites (idempotent)")
    public ResponseEntity<ApiResponse<Void>> removeFavorite(
            Authentication auth, @PathVariable Long movieId) {
        userAccountService.removeFavorite(auth.getName(), movieId);
        return ok(null, "Removed from favorites");
    }

    /**
     * Lists the caller's watchlist (TMDB ids), newest first.
     *
     * @param auth caller identity
     * @return 200 with the list
     */
    @GetMapping("/watchlist")
    @Operation(summary = "List watchlist movies")
    public ResponseEntity<ApiResponse<List<MovieListEntryResponse>>> getWatchlist(Authentication auth) {
        return ok(userAccountService.getWatchlist(auth.getName()), "Watchlist retrieved");
    }

    /**
     * Adds a movie to the watchlist (idempotent).
     *
     * @param auth    caller identity
     * @param movieId TMDB movie id
     * @return 200 acknowledgement
     */
    @PostMapping("/watchlist/{movieId}")
    @Operation(summary = "Add a movie to the watchlist (idempotent)")
    public ResponseEntity<ApiResponse<Void>> addToWatchlist(
            Authentication auth, @PathVariable Long movieId) {
        userAccountService.addToWatchlist(auth.getName(), movieId);
        return ok(null, "Added to watchlist");
    }

    /**
     * Removes a movie from the watchlist (idempotent).
     *
     * @param auth    caller identity
     * @param movieId TMDB movie id
     * @return 200 acknowledgement
     */
    @DeleteMapping("/watchlist/{movieId}")
    @Operation(summary = "Remove a movie from the watchlist (idempotent)")
    public ResponseEntity<ApiResponse<Void>> removeFromWatchlist(
            Authentication auth, @PathVariable Long movieId) {
        userAccountService.removeFromWatchlist(auth.getName(), movieId);
        return ok(null, "Removed from watchlist");
    }

    /**
     * Wraps a payload in the shared success envelope.
     *
     * @param data    payload (may be null for acknowledgements)
     * @param message human-readable outcome
     * @param <T>     payload type
     * @return 200 response
     */
    private static <T> ResponseEntity<ApiResponse<T>> ok(T data, String message) {
        return ResponseEntity.ok(ApiResponse.success(data, message, HttpStatus.OK.value()));
    }
}
