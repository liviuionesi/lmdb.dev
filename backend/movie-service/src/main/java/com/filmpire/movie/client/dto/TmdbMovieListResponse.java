package com.filmpire.movie.client.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.io.Serializable;
import java.util.List;

/**
 * TMDB API response for movie lists (discover, search, popular, etc.).
 * Serializable: the facade's list endpoints are cached via {@code @Cacheable}
 * (Redis, JDK serialization) as well as deserialized from TMDB.
 */
public record TmdbMovieListResponse(
    Integer page,
    @JsonProperty("total_pages") Integer totalPages,
    @JsonProperty("total_results") Integer totalResults,
    List<TmdbMovieItem> results
) implements Serializable {
    public record TmdbMovieItem(
        Long id,
        String title,
        String overview,
        @JsonProperty("poster_path") String posterPath,
        @JsonProperty("backdrop_path") String backdropPath,
        @JsonProperty("release_date") String releaseDate,
        @JsonProperty("vote_average") Double voteAverage,
        @JsonProperty("vote_count") Integer voteCount,
        @JsonProperty("genre_ids") List<Long> genreIds,
        Double popularity,
        Boolean adult,
        @JsonProperty("original_language") String originalLanguage
    ) implements Serializable {}
}
