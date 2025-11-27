package com.filmpire.movie.dto;

import java.io.Serializable;
import lombok.Builder;

/**
 * DTO for Crew member.
 */
@Builder
public record CrewDto(
    Long id,
    String name,
    String job,
    String department,
    String profilePath
) implements Serializable {}
