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
     * Movie title.
     */
    @Indexed
    private String title;

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
     * Spoken languages.
     */
    private List<String> spokenLanguages;

    /**
     * Production companies.
     */
    private List<ProductionCompany> productionCompanies;

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

