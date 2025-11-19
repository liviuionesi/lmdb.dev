package com.filmpire.movie.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Genre entity representing a movie genre.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Genre {
    private Long id;
    private String name;
}

