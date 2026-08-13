package dev.lmdb.movie.client.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import dev.lmdb.movie.model.Genre;
import dev.lmdb.movie.model.MovieCollection;
import dev.lmdb.movie.model.ProductionCompany;
import dev.lmdb.movie.model.ProductionCountry;
import dev.lmdb.movie.model.SpokenLanguage;
import java.time.LocalDate;
import java.util.List;

/**
 * TMDB API response for movie details. Fields mirror TMDB's real {@code /movie/{id}} shape
 * (ADR-010) so this same record can be reused to serialize our own persisted {@link
 * dev.lmdb.movie.model.Movie} back out through the facade, not just to deserialize TMDB's
 * response.
 */
public record TmdbMovieResponse(
    Long id,
    String title,
    @JsonProperty("original_title") String originalTitle,
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
    @JsonProperty("production_countries") List<ProductionCountry> productionCountries,
    @JsonProperty("belongs_to_collection") MovieCollection belongsToCollection,
    Boolean video,
    @JsonProperty("original_language") String originalLanguage,
    Double popularity,
    Boolean adult,
    @JsonProperty("imdb_id") String imdbId,
    String tagline,
    String homepage,
    @JsonInclude(JsonInclude.Include.NON_NULL) TmdbVideosResponse videos,
    @JsonInclude(JsonInclude.Include.NON_NULL) TmdbCreditsResponse credits) {}
