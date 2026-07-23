package com.filmpire.movie.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * A country involved in a movie's production, as TMDB reports it
 * (ISO 3166-1 code + name).
 *
 * <p>Implements {@link Serializable} because this type is reachable from
 * {@code MovieDto}, which Spring's Redis cache writes using JDK
 * serialization — one non-serializable nested field fails the entire write.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductionCountry implements Serializable {
    @JsonProperty("iso_3166_1")
    private String iso31661;
    private String name;
}
