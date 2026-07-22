package com.filmpire.actor.model;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * A typed, queryable actor profile — the persisted source of truth behind
 * both the native {@code /api/v1/actors} API and the TMDB-shaped facade
 * (ADR-010): a detail fetch from either surface maps TMDB's response into
 * this entity and saves it, and later requests read it back instead of
 * re-fetching. Search results are also upserted (lightweight stubs), so the
 * dataset grows from any endpoint that returns an actor, mirroring
 * movie-service's {@code Movie} entity.
 *
 * <p>DELIBERATE DEVIATION from the original §3.6 sketch: no
 * {@code @ManyToMany} actor↔movie join table. Movies live in movie-service's
 * database (database-per-service, ADR-002) — a local join table would
 * duplicate foreign data with no owner. Filmography is served live from
 * TMDB's {@code person/{id}/movie_credits} on every request instead (like
 * movie-service's list endpoints, TMDB's data isn't re-hosted here).</p>
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

    /**
     * Alternate names TMDB knows this person by (stage names, aliases).
     * EAGER: small collection, always needed alongside the rest of the
     * profile, and read outside the service's transaction boundary by the
     * facade controller — LAZY (the JPA default for element collections)
     * would throw LazyInitializationException there.
     */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "actor_also_known_as", joinColumns = @JoinColumn(name = "actor_tmdb_id"))
    @Column(name = "also_known_as")
    private List<String> alsoKnownAs;

    /** TMDB's primary department for this person (e.g. "Acting", "Directing"). */
    @Column(name = "known_for_department", length = 100)
    private String knownForDepartment;

    /** TMDB's gender code: 0 = not specified, 1 = female, 2 = male, 3 = non-binary. */
    @Column
    private Integer gender;

    /** IMDB id, if TMDB knows it. */
    @Column(name = "imdb_id", length = 20)
    private String imdbId;

    /** Personal/official homepage URL, if TMDB knows it. */
    @Column(length = 512)
    private String homepage;

    /** Adult-content flag as TMDB reports it. */
    @Column
    private Boolean adult;

    /** When this row was last refreshed from a TMDB document. */
    @Column(name = "synced_at", nullable = false)
    private LocalDateTime syncedAt;
}
