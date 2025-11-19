package com.filmpire.movie.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Credits entity containing cast and crew.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Credits {
    private Long movieId;
    private List<Cast> cast;
    private List<Crew> crew;
}

