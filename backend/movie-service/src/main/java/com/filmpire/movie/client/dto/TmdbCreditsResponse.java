package com.filmpire.movie.client.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * TMDB API response for movie credits (cast and crew).
 */
public record TmdbCreditsResponse(
    Long id,
    List<TmdbCast> cast,
    List<TmdbCrew> crew
) {
    public record TmdbCast(
        Long id,
        String name,
        String character,
        @JsonProperty("profile_path") String profilePath,
        Integer order,
        @JsonProperty("cast_id") Long castId
    ) {}

    public record TmdbCrew(
        Long id,
        String name,
        String job,
        String department,
        @JsonProperty("profile_path") String profilePath,
        @JsonProperty("credit_id") String creditId
    ) {}
}
