package com.filmpire.movie.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * Production company entity.
 *
 * <p>Implements {@link Serializable} because this type is reachable from
 * {@code MovieDto}, which Spring's Redis cache writes using JDK
 * serialization — one non-serializable nested field fails the entire write.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductionCompany implements Serializable {
    private Long id;
    private String name;
    @JsonProperty("logo_path")
    private String logoPath;
    @JsonProperty("origin_country")
    private String originCountry;
}

