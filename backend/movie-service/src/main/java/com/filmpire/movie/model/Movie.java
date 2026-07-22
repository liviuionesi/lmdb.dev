package com.filmpire.movie.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Movie entity representing a movie from TMDB.
 * Stored in MongoDB with hybrid caching strategy (MongoDB + Redis).
 */
@Document(collection = "movies")
@Getter
@Setter
@lombok.EqualsAndHashCode
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Movie {
    /**
     * MongoDB document ID.
     */
    @Id
    private String id;

    /**
     * TMDB movie ID (indexed for quick lookups).
     */
    @Indexed(unique = true)
    private Long tmdbId;

    /**
     * Movie title (localized/display title).
     */
    @Indexed
    private String title;

    /**
     * Movie title in its original language — distinct from {@link #title}
     * whenever TMDB localizes the display title.
     */
    private String originalTitle;

    /**
     * Movie overview/description.
     */
    private String overview;

    /**
     * Poster image path.
     */
    private String posterPath;

    /**
     * Backdrop image path.
     */
    private String backdropPath;

    /**
     * Release date.
     */
    @Indexed
    private LocalDate releaseDate;

    /**
     * Average vote rating.
     */
    @Indexed
    private Double voteAverage;

    /**
     * Number of votes.
     */
    private Integer voteCount;

    /**
     * List of genres.
     */
    private List<Genre> genres;

    /**
     * Runtime in minutes.
     */
    private Integer runtime;

    /**
     * Movie status (Released, Post Production, etc.).
     */
    private String status;

    /**
     * Budget in dollars.
     */
    private Long budget;

    /**
     * Revenue in dollars.
     */
    private Long revenue;

    /**
     * Spoken languages (ISO 639-1 code + name).
     */
    private List<SpokenLanguage> spokenLanguages;

    /**
     * Production companies.
     */
    private List<ProductionCompany> productionCompanies;

    /**
     * Countries involved in the movie's production (ISO 3166-1 code + name).
     */
    private List<ProductionCountry> productionCountries;

    /**
     * The franchise/collection this movie belongs to, or null for a
     * standalone movie.
     */
    private MovieCollection belongsToCollection;

    /**
     * Whether the detail response returned by TMDB includes a full-length
     * video (distinct from {@link #videos}, which are trailers/clips).
     */
    private Boolean video;

    /**
     * Original language.
     */
    private String originalLanguage;

    /**
     * Popularity score.
     */
    @Indexed
    private Double popularity;

    /**
     * Adult content flag.
     */
    private Boolean adult;

    /**
     * IMDB ID.
     */
    private String imdbId;

    /**
     * Tagline.
     */
    private String tagline;

    /**
     * Homepage URL.
     */
    private String homepage;

    /**
     * Trailers/clips, persisted alongside the movie so a detail request with
     * {@code append_to_response=videos} needs no second TMDB round trip once
     * this document has been synced at least once.
     */
    private List<Video> videos;

    /**
     * Cast and crew, persisted alongside the movie for the same reason as
     * {@link #videos} — backs {@code append_to_response=credits}.
     */
    private Credits credits;

    /**
     * Document creation timestamp.
     */
    private LocalDateTime createdAt;

    /**
     * Document last update timestamp.
     */
    private LocalDateTime updatedAt;

    /**
     * TMDB sync version for cache invalidation.
     */
    private Integer tmdbSyncVersion;
}

