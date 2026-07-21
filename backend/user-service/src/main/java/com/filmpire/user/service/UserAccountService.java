package com.filmpire.user.service;

import com.filmpire.shared.exception.ResourceNotFoundException;
import com.filmpire.shared.exception.UnauthorizedException;
import com.filmpire.shared.exception.ValidationException;
import com.filmpire.user.dto.AuthDtos.ChangePasswordRequest;
import com.filmpire.user.dto.AuthDtos.MovieListEntryResponse;
import com.filmpire.user.dto.AuthDtos.UpdateProfileRequest;
import com.filmpire.user.dto.AuthDtos.UserProfileResponse;
import com.filmpire.user.model.Favorite;
import com.filmpire.user.model.User;
import com.filmpire.user.model.WatchlistItem;
import com.filmpire.user.repository.FavoriteRepository;
import com.filmpire.user.repository.UserRepository;
import com.filmpire.user.repository.WatchlistItemRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Profile, favorites and watchlist use-cases for the authenticated user
 * (ARCHITECTURE.md §3.5, issue #17).
 *
 * <p>All methods take the caller's username (the JWT subject placed in the
 * security context by {@code JwtAuthenticationFilter}) — a user can only
 * ever act on their own data; there is no cross-user access path.</p>
 *
 * <p>Favorites/watchlist adds and removes are IDEMPOTENT: adding a movie
 * twice or removing an absent one succeeds without error, which makes the
 * endpoints safe for client retries and toggle-style UIs.</p>
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class UserAccountService {

    private final UserRepository userRepository;
    private final FavoriteRepository favoriteRepository;
    private final WatchlistItemRepository watchlistItemRepository;
    private final PasswordEncoder passwordEncoder;

    /**
     * Fetches the caller's profile.
     *
     * @param username JWT subject of the caller
     * @return profile DTO
     * @throws ResourceNotFoundException if the account vanished after token issue
     */
    @Transactional(readOnly = true)
    public UserProfileResponse getProfile(String username) {
        return AuthService.toProfile(requireUser(username));
    }

    /**
     * Updates the caller's profile (email only — username is the immutable
     * JWT subject).
     *
     * @param username JWT subject of the caller
     * @param request  new profile values
     * @return updated profile DTO
     * @throws ValidationException if the new email belongs to another account
     */
    @Transactional
    public UserProfileResponse updateProfile(String username, UpdateProfileRequest request) {
        User user = requireUser(username);

        // Allow "changing" to one's own current email; block anyone else's.
        if (!user.getEmail().equals(request.email())
                && userRepository.existsByEmail(request.email())) {
            throw new ValidationException("Email is already registered: " + request.email());
        }

        user.setEmail(request.email());
        log.info("User '{}' updated profile", username);
        return AuthService.toProfile(user);
    }

    /**
     * Changes the caller's password after verifying the current one.
     *
     * @param username JWT subject of the caller
     * @param request  current + new password
     * @throws UnauthorizedException if the current password does not match
     */
    @Transactional
    public void changePassword(String username, ChangePasswordRequest request) {
        User user = requireUser(username);

        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new UnauthorizedException("Current password is incorrect");
        }

        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        log.info("User '{}' changed password", username);
    }

    /**
     * Lists the caller's favorites, newest first.
     *
     * @param username JWT subject of the caller
     * @return favorites as movie references
     */
    @Transactional(readOnly = true)
    public List<MovieListEntryResponse> getFavorites(String username) {
        return favoriteRepository.findByUserOrderByAddedAtDesc(requireUser(username)).stream()
            .map(f -> new MovieListEntryResponse(f.getMovieId(), f.getAddedAt()))
            .toList();
    }

    /**
     * Adds a movie to favorites (idempotent).
     *
     * @param username JWT subject of the caller
     * @param movieId  TMDB movie id
     */
    @Transactional
    public void addFavorite(String username, Long movieId) {
        User user = requireUser(username);
        // Idempotent: present already → nothing to do.
        if (favoriteRepository.findByUserAndMovieId(user, movieId).isEmpty()) {
            favoriteRepository.save(Favorite.builder()
                .user(user).movieId(movieId).addedAt(LocalDateTime.now()).build());
            log.debug("User '{}' favorited movie {}", username, movieId);
        }
    }

    /**
     * Removes a movie from favorites (idempotent).
     *
     * @param username JWT subject of the caller
     * @param movieId  TMDB movie id
     */
    @Transactional
    public void removeFavorite(String username, Long movieId) {
        User user = requireUser(username);
        favoriteRepository.findByUserAndMovieId(user, movieId)
            .ifPresent(favoriteRepository::delete);
    }

    /**
     * Lists the caller's watchlist, newest first.
     *
     * @param username JWT subject of the caller
     * @return watchlist as movie references
     */
    @Transactional(readOnly = true)
    public List<MovieListEntryResponse> getWatchlist(String username) {
        return watchlistItemRepository.findByUserOrderByAddedAtDesc(requireUser(username)).stream()
            .map(w -> new MovieListEntryResponse(w.getMovieId(), w.getAddedAt()))
            .toList();
    }

    /**
     * Adds a movie to the watchlist (idempotent).
     *
     * @param username JWT subject of the caller
     * @param movieId  TMDB movie id
     */
    @Transactional
    public void addToWatchlist(String username, Long movieId) {
        User user = requireUser(username);
        if (watchlistItemRepository.findByUserAndMovieId(user, movieId).isEmpty()) {
            watchlistItemRepository.save(WatchlistItem.builder()
                .user(user).movieId(movieId).addedAt(LocalDateTime.now()).build());
            log.debug("User '{}' watchlisted movie {}", username, movieId);
        }
    }

    /**
     * Removes a movie from the watchlist (idempotent).
     *
     * @param username JWT subject of the caller
     * @param movieId  TMDB movie id
     */
    @Transactional
    public void removeFromWatchlist(String username, Long movieId) {
        User user = requireUser(username);
        watchlistItemRepository.findByUserAndMovieId(user, movieId)
            .ifPresent(watchlistItemRepository::delete);
    }

    /**
     * Resolves the caller's account or fails with 404 (the token was valid,
     * so a missing row means the account was deleted since issue).
     *
     * @param username JWT subject
     * @return account entity
     * @throws ResourceNotFoundException if no such account exists
     */
    private User requireUser(String username) {
        return userRepository.findByUsername(username)
            .orElseThrow(() -> new ResourceNotFoundException("User not found: " + username));
    }
}
