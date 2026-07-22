package com.filmpire.actor.client.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;
import java.util.List;

/**
 * TMDB API response for a person's {@code movie_credits} — the cast side
 * backs the actor's filmography.
 */
public record TmdbPersonMovieCreditsResponse(
    Long id,
    List<TmdbCastCredit> cast
) implements Serializable {

    public record TmdbCastCredit(
        Long id,
        String title,
        String character,
        @JsonProperty("release_date") String releaseDate,
        @JsonProperty("poster_path") String posterPath,
        @JsonProperty("vote_average") Double voteAverage
    ) implements Serializable {
    }
}
