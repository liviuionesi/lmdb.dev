package com.filmpire.movie.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

import java.util.List;

/**
 * Credits entity containing cast and crew.
 *
 * <p>{@link Serializable} for consistency with the rest of the embedded
 * model: any value type that can reach a @Cacheable return value must be
 * serializable, since Redis caching here uses JDK serialization.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Credits implements Serializable {
    private Long movieId;
    private List<Cast> cast;
    private List<Crew> crew;
}

