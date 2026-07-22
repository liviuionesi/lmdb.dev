package com.filmpire.movie.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A country involved in a movie's production, as TMDB reports it
 * (ISO 3166-1 code + name).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductionCountry {
    @JsonProperty("iso_3166_1")
    private String iso31661;
    private String name;
}
