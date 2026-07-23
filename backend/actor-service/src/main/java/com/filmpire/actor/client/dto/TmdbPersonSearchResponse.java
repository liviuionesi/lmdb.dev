package com.filmpire.actor.client.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;
import java.util.List;

/**
 * TMDB API response for the paged person-list endpoints — {@code /search/person}
 * and {@code /person/popular}, which share one envelope shape.
 *
 * @param page         current page (TMDB pages are 1-based)
 * @param totalPages   total pages available
 * @param totalResults total matching people
 * @param results      the people on this page
 */
public record TmdbPersonSearchResponse(
    Integer page,
    @JsonProperty("total_pages") Integer totalPages,
    @JsonProperty("total_results") Long totalResults,
    List<TmdbPersonSummary> results
) implements Serializable {

    /**
     * A person as the list endpoints report them — a lightweight subset of the
     * detail endpoint's profile, which is why upserts from here are stubs.
     *
     * @param id                 TMDB person id
     * @param name               person's name
     * @param profilePath        TMDB CDN profile path, may be null
     * @param popularity         TMDB popularity score
     * @param knownForDepartment primary department (e.g. "Acting"), may be null
     * @param gender             TMDB gender code, may be null
     * @param adult              adult-content flag, may be null
     */
    public record TmdbPersonSummary(
        Long id,
        String name,
        @JsonProperty("profile_path") String profilePath,
        Double popularity,
        @JsonProperty("known_for_department") String knownForDepartment,
        Integer gender,
        Boolean adult
    ) implements Serializable {
    }
}
