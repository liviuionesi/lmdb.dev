package com.filmpire.movie.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * Crew member entity.
 *
 * <p>{@link Serializable} for consistency with the rest of the embedded
 * model: any value type that can reach a @Cacheable return value must be
 * serializable, since Redis caching here uses JDK serialization.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Crew implements Serializable {
    private Long id;
    private String name;
    private String job;
    private String department;
    private String profilePath;
    private Long creditId;
}

