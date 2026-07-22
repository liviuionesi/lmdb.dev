package com.filmpire.movie.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * The franchise/collection a movie belongs to (e.g. "The Dark Knight
 * Collection"), as TMDB's {@code belongs_to_collection} field reports it.
 * Null for standalone movies.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MovieCollection {
    private Long id;
    private String name;
    @JsonProperty("poster_path")
    private String posterPath;
    @JsonProperty("backdrop_path")
    private String backdropPath;
}
