package com.filmpire.movie.dto;

import java.io.Serializable;
import lombok.Builder;

/**
 * DTO for Cast member.
 */
@Builder
public record CastDto(
    Long id,
    String name,
    String character,
    String profilePath,
    Integer order
) implements Serializable {}
