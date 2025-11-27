package com.filmpire.movie.dto;

import java.io.Serializable;
import lombok.Builder;

/**
 * DTO for Genre.
 */
@Builder
public record GenreDto(
    Long id,
    String name
) implements Serializable {}
