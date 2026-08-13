package dev.lmdb.movie.dto;

import dev.lmdb.movie.model.MovieCollection;
import dev.lmdb.movie.model.ProductionCompany;
import dev.lmdb.movie.model.ProductionCountry;
import dev.lmdb.movie.model.SpokenLanguage;
import java.io.Serializable;
import java.time.LocalDate;
import java.util.List;
import lombok.Builder;

/** DTO for Movie entity. */
@Builder
public record MovieDto(
    String id,
    Long tmdbId,
    String title,
    String originalTitle,
    String overview,
    String posterPath,
    String backdropPath,
    LocalDate releaseDate,
    Double voteAverage,
    Integer voteCount,
    List<GenreDto> genres,
    Integer runtime,
    String status,
    Long budget,
    Long revenue,
    List<SpokenLanguage> spokenLanguages,
    List<ProductionCompany> productionCompanies,
    List<ProductionCountry> productionCountries,
    MovieCollection belongsToCollection,
    Boolean video,
    String originalLanguage,
    Double popularity,
    Boolean adult,
    String imdbId,
    String tagline,
    String homepage)
    implements Serializable {}
