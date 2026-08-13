package dev.lmdb.movie.client.dto;

import dev.lmdb.movie.model.Genre;
import java.io.Serializable;
import java.util.List;

/**
 * TMDB API response for genres list. Serializable: cached via {@code @Cacheable} (Redis, JDK
 * serialization).
 */
public record TmdbGenresResponse(List<Genre> genres) implements Serializable {}
