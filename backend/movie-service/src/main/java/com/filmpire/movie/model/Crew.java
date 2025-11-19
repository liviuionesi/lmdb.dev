package com.filmpire.movie.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Crew member entity.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Crew {
    private Long id;
    private String name;
    private String job;
    private String department;
    private String profilePath;
    private Long creditId;
}

