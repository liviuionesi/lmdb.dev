package com.filmpire.movie.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Production company entity.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductionCompany {
    private Long id;
    private String name;
    private String logoPath;
    private String originCountry;
}

