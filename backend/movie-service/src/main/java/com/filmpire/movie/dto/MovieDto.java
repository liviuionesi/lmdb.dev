package com.filmpire.movie.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.io.Serializable;
import java.time.LocalDate;
import java.util.List;
/**
 * DTO for Movie entity.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MovieDto implements Serializable {
    private static final long serialVersionUID = 1L;
    private String id;
    private Long tmdbId;
    private String title;
    private String overview;
    private String posterPath;
    private String backdropPath;
    private LocalDate releaseDate;
    private Double voteAverage;
    private Integer voteCount;
    private List<GenreDto> genres;
    private Integer runtime;
    private String status;
    private Long budget;
    private Long revenue;
    private List<String> spokenLanguages;
    private String originalLanguage;
    private Double popularity;
    private Boolean adult;
    private String imdbId;
    private String tagline;
    private String homepage;
}
