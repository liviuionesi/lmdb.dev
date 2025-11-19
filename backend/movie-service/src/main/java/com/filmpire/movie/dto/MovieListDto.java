package com.filmpire.movie.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDate;
import java.io.Serializable;
import java.util.List;
/**
 * Simplified DTO for movie lists (discover, search, popular, etc.).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MovieListDto implements Serializable {
    private static final long serialVersionUID = 1L;
    private Long tmdbId;
    private String title;
    private String overview;
    private String posterPath;
    private String backdropPath;
    private LocalDate releaseDate;
    private Double voteAverage;
    private Integer voteCount;
    private List<GenreDto> genres;
    private Double popularity;
    private Boolean adult;
}
