package com.filmpire.user.repository;

import com.filmpire.user.model.User;
import com.filmpire.user.model.WatchlistItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * JPA repository for {@link WatchlistItem} entries.
 */
public interface WatchlistItemRepository extends JpaRepository<WatchlistItem, Long> {

    /**
     * A user's watchlist, newest first (backed by idx_watchlist_user).
     *
     * @param user owner
     * @return watchlist ordered by added time descending
     */
    List<WatchlistItem> findByUserOrderByAddedAtDesc(User user);

    /**
     * Looks up one (user, movie) watchlist entry — used for idempotent
     * add/remove.
     *
     * @param user    owner
     * @param movieId TMDB movie id
     * @return the entry, if present
     */
    Optional<WatchlistItem> findByUserAndMovieId(User user, Long movieId);
}
