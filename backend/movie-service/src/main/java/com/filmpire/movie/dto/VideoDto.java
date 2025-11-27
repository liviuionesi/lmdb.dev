package com.filmpire.movie.dto;

import java.io.Serializable;
import lombok.Builder;

/**
 * DTO for Video (trailers, clips).
 */
@Builder
public record VideoDto(
    String id,
    String key,
    String name,
    String site,
    Integer size,
    String type,
    Boolean official,
    String publishedAt
) implements Serializable {}
