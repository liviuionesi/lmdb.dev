package com.filmpire.user.repository;

import com.filmpire.user.model.Favorite;
import com.filmpire.user.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * JPA repository for {@link Favorite} entries.
 */
public interface FavoriteRepository extends JpaRepository<Favorite, Long> {

    /**
     * A user's favorites, newest first (backed by idx_favorites_user).
     *
     * @param user owner
     * @return favorites ordered by added time descending
     */
    List<Favorite> findByUserOrderByAddedAtDesc(User user);

    /**
     * Looks up one (user, movie) favorite — used for idempotent add/remove.
     *
     * @param user    owner
     * @param movieId TMDB movie id
     * @return the entry, if present
     */
    Optional<Favorite> findByUserAndMovieId(User user, Long movieId);
}
