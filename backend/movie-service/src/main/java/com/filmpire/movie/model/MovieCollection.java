package com.filmpire.movie.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * The franchise/collection a movie belongs to (e.g. "The Dark Knight
 * Collection"), as TMDB's {@code belongs_to_collection} field reports it.
 * Null for standalone movies.
 *
 * <p>Implements {@link Serializable} because this type is reachable from
 * {@code MovieDto}, which Spring's Redis cache writes using JDK
 * serialization — one non-serializable nested field fails the entire write.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MovieCollection implements Serializable {
    private Long id;
    private String name;
    @JsonProperty("poster_path")
    private String posterPath;
    @JsonProperty("backdrop_path")
    private String backdropPath;
}
