package com.filmpire.user.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * A movie a user queued to watch later.
 *
 * <p>Structurally identical to {@link Favorite} but kept as a separate
 * entity/table: the two lists have independent lifecycles and future
 * behavior (e.g. watchlist ordering, watched-state) would diverge.</p>
 */
@Entity
@Table(name = "watchlist_items",
       uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "movie_id"}))
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WatchlistItem {

    /** Surrogate primary key. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Owning user; lazy — list queries never need the full user row. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** TMDB movie id (the cross-service reference key). */
    @Column(name = "movie_id", nullable = false)
    private Long movieId;

    /** When the movie was added (list default sort, newest first). */
    @Column(name = "added_at", nullable = false)
    private LocalDateTime addedAt;
}
