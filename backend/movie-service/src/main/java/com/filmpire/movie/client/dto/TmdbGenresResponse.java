package com.filmpire.movie.client.dto;

import com.filmpire.movie.model.Genre;
import java.io.Serializable;
import java.util.List;

/**
 * TMDB API response for genres list. Serializable: cached via
 * {@code @Cacheable} (Redis, JDK serialization).
 */
public record TmdbGenresResponse(
    List<Genre> genres
) implements Serializable {}
