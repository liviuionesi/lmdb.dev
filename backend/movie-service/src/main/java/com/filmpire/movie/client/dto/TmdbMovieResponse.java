package com.filmpire.movie.client.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.filmpire.movie.model.Genre;
import com.filmpire.movie.model.ProductionCompany;
import java.time.LocalDate;
import java.util.List;

/**
 * TMDB API response for movie details.
 */
public record TmdbMovieResponse(
    Long id,
    String title,
    String overview,
    @JsonProperty("poster_path") String posterPath,
    @JsonProperty("backdrop_path") String backdropPath,
    @JsonProperty("release_date") LocalDate releaseDate,
    @JsonProperty("vote_average") Double voteAverage,
    @JsonProperty("vote_count") Integer voteCount,
    List<Genre> genres,
    Integer runtime,
    String status,
    Long budget,
    Long revenue,
    @JsonProperty("spoken_languages") List<SpokenLanguage> spokenLanguages,
    @JsonProperty("production_companies") List<ProductionCompany> productionCompanies,
    @JsonProperty("original_language") String originalLanguage,
    Double popularity,
    Boolean adult,
    @JsonProperty("imdb_id") String imdbId,
    String tagline,
    String homepage
) {
    public record SpokenLanguage(
        @JsonProperty("iso_639_1") String iso6391,
        String name
    ) {}
}
