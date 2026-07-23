package com.filmpire.actor.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One profile image TMDB knows for an actor, persisted as part of
 * {@link Actor}'s image set so {@code GET /person/{id}/images} is served from
 * actor-service's own data (ADR-010) rather than proxied.
 *
 * <p>Stores TMDB's CDN <em>reference</em> and metadata only — never the image
 * bytes (ARCHITECTURE.md §3.8). Clients resolve {@code filePath} against
 * {@code image.tmdb.org} themselves, exactly as they do with the real API.</p>
 */
@Embeddable
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ActorProfileImage {

    /** TMDB CDN path, e.g. {@code /kU3B75TyRiCgE270EyZnHjfivoq.jpg}. */
    @Column(name = "file_path", nullable = false, length = 255)
    private String filePath;

    /** Width divided by height, as TMDB reports it. */
    @Column(name = "aspect_ratio")
    private Double aspectRatio;

    /** Image height in pixels. */
    @Column
    private Integer height;

    /** Image width in pixels. */
    @Column
    private Integer width;

    /** ISO 639-1 language tag, null for language-neutral images. */
    @Column(name = "iso_639_1", length = 10)
    private String iso6391;

    /** TMDB community vote average for this image. */
    @Column(name = "vote_average")
    private Double voteAverage;

    /** Number of TMDB community votes for this image. */
    @Column(name = "vote_count")
    private Integer voteCount;
}
