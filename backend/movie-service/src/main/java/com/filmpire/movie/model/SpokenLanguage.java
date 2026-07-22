package com.filmpire.movie.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A language spoken in a movie, as TMDB reports it (ISO 639-1 code + name).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SpokenLanguage {
    @JsonProperty("iso_639_1")
    private String iso6391;
    private String name;
}
