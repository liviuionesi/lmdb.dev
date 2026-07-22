package com.filmpire.actor.client.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.List;

/**
 * TMDB API response for person (actor) details. Fields mirror TMDB's real
 * {@code /person/{id}} shape (ADR-010) so this same record can be reused to
 * serialize our own persisted {@link com.filmpire.actor.model.Actor} back
 * out through the facade, not just to deserialize TMDB's response.
 * Serializable: read-through lookups are not currently cached, but nothing
 * else in this codebase's TMDB response records is either — kept consistent.
 */
public record TmdbPersonResponse(
    Long id,
    String name,
    String biography,
    LocalDate birthday,
    @JsonProperty("place_of_birth") String placeOfBirth,
    @JsonProperty("profile_path") String profilePath,
    Double popularity,
    @JsonProperty("also_known_as") List<String> alsoKnownAs,
    @JsonProperty("known_for_department") String knownForDepartment,
    Integer gender,
    @JsonProperty("imdb_id") String imdbId,
    String homepage,
    Boolean adult
) implements Serializable {
}
