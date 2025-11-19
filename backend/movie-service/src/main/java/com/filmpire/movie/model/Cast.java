package com.filmpire.movie.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Cast member entity.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Cast {
    private Long id;
    private String name;
    private String character;
    private String profilePath;
    private Integer order;
    private Long castId;
}

