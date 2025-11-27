package com.filmpire.movie.dto;

import java.io.Serializable;
import java.util.List;
import lombok.Builder;

/**
 * DTO for Credits (cast and crew).
 */
@Builder
public record CreditsDto(
    Long movieId,
    List<CastDto> cast,
    List<CrewDto> crew
) implements Serializable {}
