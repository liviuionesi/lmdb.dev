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
 * A movie a user marked as favorite.
 *
 * <p>References the movie by its TMDB id only — movie data itself lives in
 * movie-service (database-per-service, ADR-002); clients hydrate details via
 * the facade. The (user, movie) pair is unique so a movie can be favorited
 * at most once; the service layer treats repeat adds as idempotent.</p>
 */
@Entity
@Table(name = "favorites",
       uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "movie_id"}))
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Favorite {

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

    /** When the movie was favorited (list default sort, newest first). */
    @Column(name = "added_at", nullable = false)
    private LocalDateTime addedAt;
}
