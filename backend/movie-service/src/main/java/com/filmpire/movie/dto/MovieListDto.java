package com.filmpire.movie.dto;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.List;
import lombok.Builder;

/**
 * Simplified DTO for movie lists (discover, search, popular, etc.).
 */
@Builder
public record MovieListDto(
    Long tmdbId,
    String title,
    String overview,
    String posterPath,
    String backdropPath,
    LocalDate releaseDate,
    Double voteAverage,
    Integer voteCount,
    List<GenreDto> genres,
    Double popularity,
    Boolean adult
) implements Serializable {}
