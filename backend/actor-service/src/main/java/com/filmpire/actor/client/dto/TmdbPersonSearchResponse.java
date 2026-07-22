package com.filmpire.actor.client.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;
import java.util.List;

/**
 * TMDB API response for {@code /search/person}.
 */
public record TmdbPersonSearchResponse(
    Integer page,
    @JsonProperty("total_pages") Integer totalPages,
    @JsonProperty("total_results") Long totalResults,
    List<TmdbPersonSummary> results
) implements Serializable {

    public record TmdbPersonSummary(
        Long id,
        String name,
        @JsonProperty("profile_path") String profilePath,
        Double popularity
    ) implements Serializable {
    }
}
