package com.filmpire.actor.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * A typed, queryable actor profile (ARCHITECTURE.md §3.6), populated as a
 * side effect of facade fetches: whenever {@code /api/v1/actors/{id}} parses
 * a raw TMDB person document, the core fields are upserted here.
 *
 * <p>DELIBERATE DEVIATION from the original §3.6 sketch: no
 * {@code @ManyToMany} actor↔movie join table. Movies live in movie-service's
 * database (database-per-service, ADR-002) — a local join table would
 * duplicate foreign data with no owner. Filmography is served by parsing
 * TMDB's {@code person/{id}/movie_credits} through the facade instead.</p>
 */
@Entity
@Table(name = "actors")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Actor {

    /** TMDB person id — natural primary key (no surrogate needed). */
    @Id
    @Column(name = "tmdb_id")
    private Long tmdbId;

    /** Actor's name. */
    @Column(nullable = false, length = 255)
    private String name;

    /** Biography text (TMDB serves long ones — unbounded TEXT). */
    @Column(columnDefinition = "text")
    private String biography;

    /** Birth date, if TMDB knows it. */
    @Column(name = "birth_date")
    private LocalDate birthDate;

    /** Birthplace, if TMDB knows it. */
    @Column(name = "birth_place", length = 255)
    private String birthPlace;

    /** TMDB profile image path (client builds the full image URL). */
    @Column(name = "profile_path", length = 255)
    private String profilePath;

    /** TMDB popularity score at last sync. */
    @Column
    private Double popularity;

    /** When this row was last refreshed from a TMDB document. */
    @Column(name = "synced_at", nullable = false)
    private LocalDateTime syncedAt;
}
