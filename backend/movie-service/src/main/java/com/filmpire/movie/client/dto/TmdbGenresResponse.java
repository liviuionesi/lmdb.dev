package com.filmpire.movie.client.dto;

import com.filmpire.movie.model.Genre;
import java.util.List;

/**
 * TMDB API response for genres list.
 */
public record TmdbGenresResponse(
    List<Genre> genres
) {}
