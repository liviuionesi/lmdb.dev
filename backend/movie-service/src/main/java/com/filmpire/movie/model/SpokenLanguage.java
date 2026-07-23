package com.filmpire.movie.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * A language spoken in a movie, as TMDB reports it (ISO 639-1 code + name).
 *
 * <p>Implements {@link Serializable} because this type is reachable from
 * {@code MovieDto}, which Spring's Redis cache writes using JDK
 * serialization — one non-serializable nested field fails the entire write.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SpokenLanguage implements Serializable {
    @JsonProperty("iso_639_1")
    private String iso6391;
    private String name;
}
