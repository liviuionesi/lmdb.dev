package com.filmpire.movie.dto;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.List;
import lombok.Builder;

/**
 * DTO for Movie entity.
 */
@Builder
public record MovieDto(
    String id,
    Long tmdbId,
    String title,
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
    List<String> spokenLanguages,
    String originalLanguage,
    Double popularity,
    Boolean adult,
    String imdbId,
    String tagline,
    String homepage
) implements Serializable {}
